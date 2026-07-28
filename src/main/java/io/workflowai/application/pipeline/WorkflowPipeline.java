package io.workflowai.application.pipeline;

import io.workflowai.application.LlmProviderRegistry;
import io.workflowai.application.StagesProperties;
import io.workflowai.domain.exceptions.ClassificationException;
import io.workflowai.domain.exceptions.PipelineStageException;
import io.workflowai.domain.exceptions.WorkflowExecutionException;
import io.workflowai.domain.model.AgentProperties;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.domain.model.ConversationMessageRole;
import io.workflowai.domain.model.LlmRequest;
import io.workflowai.domain.model.RoutingDecision;
import io.workflowai.domain.workflow.GuardrailChecker;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.response.ResponseValidator;
import io.workflowai.domain.workflow.response.ValidationResult;
import io.workflowai.ports.outbound.AgentMemoryStorage;
import io.workflowai.ports.outbound.LlmProvider;
import io.workflowai.ports.outbound.MessageStorage;
import io.workflowai.ports.outbound.NotificationChannel;
import io.workflowai.ports.outbound.PipelineEventStreamer;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.workflowai.application.StagesProperties.StageProperties;
import static io.workflowai.domain.workflow.WorkflowPrompts.CLASSIFICATION_SYSTEM_PROMPT;
import static io.workflowai.domain.workflow.WorkflowPrompts.GUARDRAIL_FALLBACK_MESSAGE;
import static io.workflowai.domain.workflow.WorkflowPrompts.clarificationPrompt;
import static io.workflowai.domain.workflow.WorkflowPrompts.classificationPrompt;
import static io.workflowai.domain.workflow.WorkflowPrompts.greetingPrompt;
import static io.workflowai.domain.workflow.WorkflowPrompts.memoryCompactionPrompt;
import static io.workflowai.domain.workflow.WorkflowPrompts.redirectPrompt;
import static io.workflowai.domain.workflow.WorkflowPrompts.refusalPrompt;
import static io.workflowai.domain.workflow.WorkflowPrompts.retryPrompt;
import static io.workflowai.domain.workflow.WorkflowPrompts.withResponseContractInstructions;

public class WorkflowPipeline {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPipeline.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;

    private final AgentProperties agentProperties;
    private final LlmProviderRegistry llmProviderRegistry;
    private final MessageStorage messageStorage;
    private final AgentMemoryStorage agentMemoryStorage;
    private final List<PipelineEventStreamer> pipelineEventStreamers;
    private final List<NotificationChannel> notificationChannels;
    private final GuardrailChecker guardrailChecker;
    private final Map<StageId, StageProperties> stagePropertiesMap;
    private final ResponseValidator responseValidator;
    private final JsonMapper jsonMapper;

    private CompiledGraph<WorkflowContext> graph;

    public WorkflowPipeline(
            AgentProperties agentProperties,
            LlmProviderRegistry llmProviderRegistry,
            StagesProperties stagesProperties,
            MessageStorage messageStorage,
            AgentMemoryStorage agentMemoryStorage,
            List<PipelineEventStreamer> pipelineEventStreamers,
            List<NotificationChannel> notificationChannels,
            GuardrailChecker guardrailChecker,
            JsonMapper jsonMapper) {
        this.agentProperties = agentProperties;
        this.llmProviderRegistry = llmProviderRegistry;
        this.messageStorage = messageStorage;
        this.agentMemoryStorage = agentMemoryStorage;
        this.pipelineEventStreamers = pipelineEventStreamers;
        this.notificationChannels = notificationChannels;
        this.guardrailChecker = guardrailChecker;
        this.responseValidator = new ResponseValidator(jsonMapper);
        this.stagePropertiesMap = stagesProperties.stages().stream()
                .collect(Collectors.toConcurrentMap(StageProperties::stageId, Function.identity()));
        this.jsonMapper = jsonMapper;
        log.debug("WorkflowPipeline initialised for agent [{}]", agentProperties.id());
    }

    public void setGraph(CompiledGraph<WorkflowContext> graph) {
        this.graph = graph;
    }

    public void execute(UUID runId, AgentRequest request) {
        UUID conversationId = request.conversationId();

        log.debug("Starting workflow execution for agent [{}], conversation [{}], run [{}], llm: [{}]", agentProperties.id(), conversationId, runId, agentProperties.model());

        Map<String, Object> initialState = Map.of(
                WorkflowContext.KEY_RUN_ID, runId,
                WorkflowContext.KEY_CONVERSATION_ID, conversationId,
                WorkflowContext.KEY_USER_MESSAGE, request.message(),
                WorkflowContext.KEY_TRIGGER_SOURCE, request.triggerSource(),
                WorkflowContext.KEY_SYSTEM_PROMPT, agentProperties.systemPrompt()
        );
        CompletableFuture<Void> future = null;
        try {
            // Wrap graph invocation with timeout to prevent indefinite blocking
            future = CompletableFuture.runAsync(() -> graph.invoke(initialState));
            future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Workflow completed for agent [{}], conversation [{}], run [{}]", agentProperties.id(), conversationId, runId);
        } catch (TimeoutException ex) {
            throw new WorkflowExecutionException(agentProperties.id(), "Workflow execution timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds", ex);
        } catch (WorkflowExecutionException ex) {
            if (future != null) future.cancel(true);
            throw ex;
        } catch (Exception ex) {
            throw new WorkflowExecutionException(agentProperties.id(), "Unexpected pipeline failure: " + ex.getMessage(), ex);
        }
    }

    public String workflowDiagram(String title) {
        if (graph == null) {
            throw new IllegalStateException("Pipeline not fully initialised — compiled graph is missing");
        }
        return graph.getGraph(GraphRepresentation.Type.MERMAID, title).content();
    }

    // ── Node Action Factory ─────────────────────────────────────────────────

    protected NodeAction<WorkflowContext> stageAction(StageId stageId, NodeAction<WorkflowContext> action) {
        return state -> {
            try {
                return action.apply(state);
            } catch (PipelineStageException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new PipelineStageException(agentProperties.id(), stageId, ex.getMessage(), ex);
            }
        };
    }

    // ── Stage Implementations ──────────────────────────────────────────────

    protected Map<String, Object> guardrailInput(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GUARDRAIL_INPUT));

        boolean blocked = guardrailChecker.checkInput(state.userMessage()).isPresent();
        if (blocked) {
            log.warn("[{}] Input guardrail blocked user message — matched blocklist term", agentProperties.id());
        }

        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GUARDRAIL_INPUT));
        return Map.of(WorkflowContext.KEY_GUARDRAIL_BLOCKED, blocked);
    }

    protected Map<String, Object> persistUserMessage(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.PERSIST_USER_MESSAGE));
        messageStorage.save(
                state.conversationId(),
                agentProperties.id(),
                new ConversationMessage(ConversationMessageRole.USER, state.userMessage(), state.guardrailPassed()));
        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.PERSIST_USER_MESSAGE));
        log.debug("[{}] User message persisted for conversation [{}]", agentProperties.id(), state.conversationId());
        return Map.of();
    }

    protected Map<String, Object> loadMemory(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.LOAD_MEMORY));

        String memoryContext = "";
        if (agentProperties.memoryEnabled()) {
            memoryContext = agentMemoryStorage
                    .getMemory(state.conversationId(), agentProperties.id())
                    .orElse("");
            log.debug("[{}] Loaded compact memory ({} chars)", agentProperties.id(), memoryContext.length());
        }

        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.LOAD_MEMORY));
        return Map.of(WorkflowContext.KEY_MEMORY_CONTEXT, memoryContext);
    }

    protected Map<String, Object> classify(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.CLASSIFICATION));

        RoutingDecision decision = performClassification(state);
        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.CLASSIFICATION));

        pipelineEventStreamers.forEach(s -> s.decisionMade(state.runId(), decision));

        log.debug("[{}] Classification result: {} — {}", agentProperties.id(), decision.decisionMode(), decision.reason());

        return Map.of(WorkflowContext.KEY_ROUTING_DECISION, decision);
    }

    protected Map<String, Object> executeWorkflow(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.EXECUTE_WORKFLOW));

        LlmRequest request = new LlmRequest(
                agentProperties.model(),
                agentProperties.temperature(),
                withResponseContractInstructions(state.systemPrompt(), agentProperties.workflowPolicyProperties().responseContract()),
                state.userMessage(),
                state.memoryContext());

        log.debug("[{}] Starting LLM call with model [{}]", agentProperties.id(), agentProperties.model());
        // Buffered, not streamed live. Note this is only a *draft* — SELF_VERIFICATION decides
        // whether it's final or needs a retry, so guardrail-check/persist/stream must not happen
        // here. That single, exactly-once step lives in self-Verify (see applyOutputGuardrail).
        String response = llmProviderRegistry.get(agentProperties.llmProviderId()).stream(request, _ -> {
        });

        ValidationResult validation = responseValidator
                .validate(agentProperties.workflowPolicyProperties().responseContract(), response);
        if (!validation.valid()) {
            log.warn("[{}] Generated response failed validation: {}", agentProperties.id(), validation.reason());
        }

        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.EXECUTE_WORKFLOW));
        log.debug("[{}] LLM streaming complete, length: {}", agentProperties.id(), response.length());

        return Map.of(
                WorkflowContext.KEY_GENERATED_RESPONSE, response,
                WorkflowContext.KEY_VALIDATION_PASSED, validation.valid(),
                WorkflowContext.KEY_VALIDATION_FAILURE_REASON, validation.reason() == null ? "" : validation.reason()
        );
    }

    protected Map<String, Object> generateClarification(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GENERATE_CLARIFICATION));

        String clarification = state.routingDecision()
                .map(RoutingDecision::clarificationQuestion)
                .filter(q -> !q.isBlank())
                .orElseGet(() -> {
                    log.debug("[{}] No clarification question from classifier, generating via LLM", agentProperties.id());
                    return executeGenerateClarification(state);
                });

        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GENERATE_CLARIFICATION));
        String safeClarification = applyOutputGuardrail(state, clarification);
        log.debug("[{}] Clarification question generated", agentProperties.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeClarification);
    }

    protected Map<String, Object> generateRedirect(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GENERATE_REDIRECT));

        String redirect = streamDecisionResponse(state, StageId.GENERATE_REDIRECT, redirectPrompt(
                state.systemPrompt(), agentProperties.workflowPolicyProperties(),
                state.routingDecision().orElse(RoutingDecision.redirect("Redirecting mixed-scope request", state.userMessage()))));

        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GENERATE_REDIRECT));
        String safeRedirect = applyOutputGuardrail(state, redirect);
        log.debug("[{}] Redirect response sent", agentProperties.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRedirect);
    }

    protected Map<String, Object> generateGreeting(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GENERATE_GREETING));

        RoutingDecision decision = state.routingDecision()
                .orElse(RoutingDecision.greet("Greeting", state.userMessage()));
        String greeting = streamDecisionResponse(state, StageId.GENERATE_GREETING, greetingPrompt(
                state.systemPrompt(), agentProperties.workflowPolicyProperties(), decision));

        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GENERATE_GREETING));
        String safeGreeting = applyOutputGuardrail(state, greeting);
        log.debug("[{}] Greeting response sent", agentProperties.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeGreeting);

    }

    protected Map<String, Object> generateRefusal(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GENERATE_REFUSAL));

        String refusal = streamDecisionResponse(state, StageId.GENERATE_REFUSAL, refusalPrompt(
                state.systemPrompt(), agentProperties.workflowPolicyProperties(),
                state.routingDecision().orElse(RoutingDecision.refuse("Refusing request", state.userMessage()))));

        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GENERATE_REFUSAL));
        String safeRefusal = applyOutputGuardrail(state, refusal);
        log.debug("[{}] Refusal response sent", agentProperties.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRefusal);

    }

    protected Map<String, Object> selfVerify(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.SELF_VERIFICATION));

        if (state.validationPassed()) {
            pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.SELF_VERIFICATION));
            String safeResponse = applyOutputGuardrail(state, state.generatedResponse().orElse(""));
            return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeResponse, WorkflowContext.KEY_VALIDATION_PASSED, true);
        }

        if (state.retried()) {
            log.warn("[{}] Self-verification failed twice ({}) — returning best effort",
                    agentProperties.id(), state.validationFailureReason());
            String failureReason = "Response still invalid after retry (%s) — returning best effort".formatted(state.validationFailureReason());
            pipelineEventStreamers.forEach(s -> s.stageFailed(state.runId(), StageId.SELF_VERIFICATION, failureReason));
            String safeRetried = applyOutputGuardrail(state, state.generatedResponse().orElse(""));
            return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRetried, WorkflowContext.KEY_VALIDATION_PASSED, true);
        }

        log.debug("[{}] Self-verification failed ({}) — attempting one retry",
                agentProperties.id(), state.validationFailureReason());

        String retryPrompt = retryPrompt(state.userMessage(), state.generatedResponse().orElse(""), state.validationFailureReason());
        var retryRequest = new LlmRequest(
                agentProperties.model(), agentProperties.temperature(),
                withResponseContractInstructions(state.systemPrompt(), agentProperties.workflowPolicyProperties().responseContract()),
                retryPrompt, state.memoryContext());

        // Buffered for the same reason as executeWorkflow: this must be the complete retry text
        // before it can be re-validated below.
        String retryResponse = llmProviderRegistry.get(agentProperties.llmProviderId()).stream(retryRequest, _ -> {
        });

        ValidationResult retryValidation = responseValidator
                .validate(agentProperties.workflowPolicyProperties().responseContract(), retryResponse);

        if (retryValidation.valid()) {
            log.debug("[{}] Retry passed validation", agentProperties.id());
            pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.SELF_VERIFICATION));
            String safeRetry = applyOutputGuardrail(state, retryResponse);
            return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRetry,
                    WorkflowContext.KEY_VALIDATION_PASSED, true,
                    WorkflowContext.KEY_RETRIED, true);
        }

        log.warn("[{}] Retry still invalid ({}) — returning latest retry result", agentProperties.id(), retryValidation.reason());
        pipelineEventStreamers.forEach(s -> s.stageFailed(state.runId(), StageId.SELF_VERIFICATION,
                "Retry still invalid (%s) — returning latest retry result".formatted(retryValidation.reason())));

        String safeRetried = applyOutputGuardrail(state, retryResponse);
        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRetried,
                WorkflowContext.KEY_VALIDATION_PASSED, true,
                WorkflowContext.KEY_RETRIED, true);
    }

    protected Map<String, Object> compactMemory(WorkflowContext state) {
        if (!agentProperties.memoryEnabled()) {
            return Map.of();
        }

        String previousMemory = state.memoryContext();
        String userMessage = state.userMessage();
        String response = state.generatedResponse().orElse("");

        // compact memory without affecting streaming the response back, can affect next request if memory compaction took too long
        Thread.startVirtualThread(() -> compactMemoryAsync(state.conversationId(), previousMemory, userMessage, response));
        return Map.of();
    }

    protected Map<String, Object> complete(WorkflowContext state) {
        pipelineEventStreamers.forEach(s -> s.conversationCompleted(state.runId()));
        ConversationMessage message = new ConversationMessage(ConversationMessageRole.AGENT,
                state.generatedResponse().orElse(""), state.guardrailPassed());
        notificationChannels.forEach(channel -> channel.notify(agentProperties.id(), state.conversationId(), message));
        log.debug("[{}] Pipeline complete for conversation [{}]", agentProperties.id(), state.conversationId());
        return Map.of();
    }

    // ── Classification ─────────────────────────────────────────────────────

    private RoutingDecision performClassification(WorkflowContext state) {
        String classificationPrompt = classificationPrompt(agentProperties.id(), agentProperties.workflowPolicyProperties(), state.userMessage());

        StageProperties stageProperties = stagePropertiesMap.get(StageId.CLASSIFICATION);
        LlmProvider stageLlmProvider = llmProviderRegistry.get(stageProperties.llmProviderId());

        LlmRequest classifyRequest = new LlmRequest(
                stageProperties.model(), 0.1,
                CLASSIFICATION_SYSTEM_PROMPT,
                classificationPrompt,
                "");
        try {
            String jsonResponse = stageLlmProvider.call(classifyRequest);
            return parseRoutingDecision(jsonResponse);
        } catch (ClassificationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("[{}] Classification LLM call failed, defaulting to REFUSE: {}", agentProperties.id(), ex.getMessage());
            return RoutingDecision.refuse("Classification unavailable: " + ex.getMessage(), state.userMessage());
        }
    }

    // Shared by every node that produces a final response (EXECUTE_WORKFLOW, GENERATE_CLARIFICATION,
    // GENERATE_REDIRECT, APPLY_REFUSE, and the self-verification retry). The candidate text must
    // already be fully generated/known — never partially streamed — so it can be checked before
    // anything reaches the client. Emits the (possibly replaced) text as simulated token chunks over
    // the existing PipelineEvent.Token schema, so chat.js needs no changes: it just receives a burst
    // of tokens close together instead of generation-paced ones.
    private String applyOutputGuardrail(WorkflowContext state, String candidateResponse) {
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GUARDRAIL_OUTPUT));

        String finalResponse = guardrailChecker.checkOutput(candidateResponse)
                .map(_ -> {
                    log.warn("[{}] Output guardrail blocked generated response — matched blocklist term",
                            agentProperties.id());
                    return GUARDRAIL_FALLBACK_MESSAGE;
                })
                .orElse(candidateResponse);

        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GUARDRAIL_OUTPUT));

        // Persisted before a single token reaches the client. If the SSE connection drops for any
        // reason during the loop below, the response is already durably saved — reopening the
        // conversation shows it regardless of whether the client received any of the stream.
        pipelineEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.PERSIST_RESPONSE));
        messageStorage.save(
                state.conversationId(),
                agentProperties.id(),
                new ConversationMessage(ConversationMessageRole.AGENT, finalResponse, false));
        pipelineEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.PERSIST_RESPONSE));

        pipelineEventStreamers.forEach(s -> s.token(state.runId(), finalResponse));

        pipelineEventStreamers.forEach(s -> s.responseCompleted(state.runId(), finalResponse));
        return finalResponse;
    }

    private RoutingDecision parseRoutingDecision(String jsonResponse) {
        try {
            return jsonMapper.readValue(jsonResponse, RoutingDecision.class);
        } catch (Exception ex) {
            throw new ClassificationException(agentProperties.id(), "Failed to parse routing decision from LLM response: %s".formatted(jsonResponse), ex);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String executeGenerateClarification(WorkflowContext state) {
        StageProperties stageProperties = stagePropertiesMap.get(StageId.GENERATE_CLARIFICATION);
        LlmProvider stageLlmProvider = llmProviderRegistry.get(stageProperties.llmProviderId());

        String prompt = clarificationPrompt(state.userMessage());
        LlmRequest request = new LlmRequest(stageProperties.model(), stageProperties.temperature(), state.systemPrompt(), prompt, state.memoryContext());
        return stageLlmProvider.call(request);
    }

    private String streamDecisionResponse(WorkflowContext state, StageId stageId, String prompt) {
        StageProperties stageProperties = stagePropertiesMap.get(stageId);
        LlmProvider stageLlmProvider = llmProviderRegistry.get(stageProperties.llmProviderId());

        try {
            LlmRequest request = new LlmRequest(stageProperties.model(), stageProperties.temperature(), state.systemPrompt(), prompt, state.memoryContext());
            return stageLlmProvider.stream(request, _ -> {
            });
        } catch (Exception ex) {
            log.warn("[{}] Decision response generation failed, using fallback: {}", agentProperties.id(), ex.getMessage());
            return agentProperties.workflowPolicyProperties().failedToProcessMessage();
        }
    }

    private void compactMemoryAsync(UUID conversationId, String previousMemory, String userMessage, String response) {
        if (response.isBlank()) {
            return;
        }

        StageProperties stageProperties = stagePropertiesMap.get(StageId.COMPACT_MEMORY);
        LlmProvider stageLlmProvider = llmProviderRegistry.get(stageProperties.llmProviderId());

        try {
            String prompt = memoryCompactionPrompt(previousMemory, userMessage, response);
            LlmRequest request = new LlmRequest(stageProperties.model(), stageProperties.temperature(), agentProperties.systemPrompt(), prompt, previousMemory);
            String compacted = stageLlmProvider.call(request);
            if (!compacted.isBlank()) {
                agentMemoryStorage.replace(conversationId, agentProperties.id(), compacted);
                log.debug("[{}] Memory compacted for conversation [{}]", agentProperties.id(), conversationId);
            }
        } catch (Exception ex) {
            log.warn("[{}] Memory compaction failed for conversation [{}]: {}", agentProperties.id(), conversationId, ex.getMessage());
        }
    }
}