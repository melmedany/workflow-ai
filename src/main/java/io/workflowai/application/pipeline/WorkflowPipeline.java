package io.workflowai.application.pipeline;

import io.workflowai.adapters.DefaultStageLabelProvider;
import io.workflowai.application.LLMProviderRegistry;
import io.workflowai.application.StagesProperties;
import io.workflowai.domain.exceptions.ClassificationException;
import io.workflowai.domain.exceptions.PipelineStageException;
import io.workflowai.domain.exceptions.WorkflowExecutionException;
import io.workflowai.domain.model.AgentProperties;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.domain.model.ConversationMessageRole;
import io.workflowai.domain.model.LLMRequest;
import io.workflowai.domain.model.RoutingDecision;
import io.workflowai.domain.workflow.GuardrailChecker;
import io.workflowai.domain.workflow.PipelineEvent;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.response.ResponseValidator;
import io.workflowai.domain.workflow.response.ValidationResult;
import io.workflowai.ports.outbound.AgentMemoryStorage;
import io.workflowai.ports.outbound.LLLMProvider;
import io.workflowai.ports.outbound.MessageStorage;
import io.workflowai.ports.outbound.NotificationChannel;
import io.workflowai.ports.outbound.RunHistoryPort;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
    private final LLMProviderRegistry llmProviderRegistry;
    private final MessageStorage messageStorage;
    private final AgentMemoryStorage agentMemoryStorage;
    private final RunHistoryPort runHistoryPort;
    private final List<NotificationChannel> notificationChannels;
    private final GuardrailChecker guardrailChecker;
    private final DefaultStageLabelProvider labelProvider;
    private final Map<StageId, StageProperties> stagePropertiesMap;
    private final ResponseValidator responseValidator;
    private final JsonMapper jsonMapper;

    private CompiledGraph<WorkflowContext> graph;

    /**
     * Keyed by per-execution runId, NOT conversationId. Two concurrent invocations for the
     * same conversation (double-submit, caller-side retry, etc.) must not share a consumer —
     * keying by conversationId previously let one run's consumer be overwritten/removed by
     * another run of the same conversation, causing cross-talk or a NoSuchElement on consumer(state).
     */
    private final ConcurrentHashMap<UUID, Consumer<PipelineEvent>> activeConsumers = new ConcurrentHashMap<>();

    public WorkflowPipeline(
            AgentProperties agentProperties,
            LLMProviderRegistry llmProviderRegistry,
            StagesProperties stagesProperties,
            MessageStorage messageStorage,
            AgentMemoryStorage agentMemoryStorage,
            RunHistoryPort runHistoryPort,
            List<NotificationChannel> notificationChannels,
            GuardrailChecker guardrailChecker,
            JsonMapper jsonMapper) {
        this.agentProperties = agentProperties;
        this.llmProviderRegistry = llmProviderRegistry;
        this.messageStorage = messageStorage;
        this.agentMemoryStorage = agentMemoryStorage;
        this.runHistoryPort = runHistoryPort;
        this.notificationChannels = notificationChannels;
        this.guardrailChecker = guardrailChecker;
        this.labelProvider = new DefaultStageLabelProvider();
        this.responseValidator = new ResponseValidator(jsonMapper);
        this.stagePropertiesMap = stagesProperties.stages().stream()
                .collect(Collectors.toConcurrentMap(StageProperties::stageId, Function.identity()));
        this.jsonMapper = jsonMapper;
        log.debug("WorkflowPipeline initialised for agent [{}]", agentProperties.id());
    }

    public void setGraph(CompiledGraph<WorkflowContext> graph) {
        this.graph = graph;
    }

    public void execute(UUID runId, AgentRequest request, Consumer<PipelineEvent> eventConsumer) {
        UUID conversationId = request.conversationId();
        AtomicBoolean runFinished = new AtomicBoolean(false);

        log.debug("Starting workflow execution for agent [{}], conversation [{}], run [{}], llm: [{}]", agentProperties.id(), conversationId, runId, agentProperties.model());

        Consumer<PipelineEvent> recordingConsumer = recordingConsumer(runId, runFinished, eventConsumer);
        activeConsumers.put(runId, recordingConsumer);

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
            WorkflowExecutionException failure = new WorkflowExecutionException(agentProperties.id(),
                    "Workflow execution timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds", ex);
            recordFailure(runId, runFinished, failure);
            throw failure;
        } catch (WorkflowExecutionException ex) {
            if (future != null) future.cancel(true);
            recordFailure(runId, runFinished, ex);
            throw ex;
        } catch (Exception ex) {
            WorkflowExecutionException failure = new WorkflowExecutionException(agentProperties.id(),
                    "Unexpected pipeline failure: " + ex.getMessage(), ex);
            recordFailure(runId, runFinished, failure);
            throw failure;
        } finally {
            activeConsumers.remove(runId);
        }
    }

    public String workflowDiagram(String title) {
        if (graph == null) {
            throw new IllegalStateException("Pipeline not fully initialised — compiled graph is missing");
        }
        return graph.getGraph(GraphRepresentation.Type.MERMAID, title).content();
    }

    // ── Event Consumer Management ───────────────────────────────────────────

    private Consumer<PipelineEvent> recordingConsumer(UUID historyRunId, AtomicBoolean runFinished, Consumer<PipelineEvent> delegate) {
        return event -> {
            if (event instanceof PipelineEvent.ConversationCompleted && runFinished.compareAndSet(false, true)) {
                runHistoryPort.complete(historyRunId);
            } else if (event instanceof PipelineEvent.Error(String message) && runFinished.compareAndSet(false, true)) {
                runHistoryPort.fail(historyRunId, message);
            }

            try {
                delegate.accept(event);
            } catch (RuntimeException ex) {
                log.warn("Pipeline event consumer failed for run [{}]: {}", historyRunId, ex.getMessage());
            }
        };
    }

    private void recordFailure(UUID historyRunId, AtomicBoolean runFinished, Exception failure) {
        if (runFinished.compareAndSet(false, true)) {
            runHistoryPort.fail(historyRunId, failure.getMessage());
        }
    }

    /**
     * Retrieves the event consumer registered for the given workflow state.
     * Called by node implementations to emit events during graph execution.
     */
    private Consumer<PipelineEvent> consumer(WorkflowContext state) {
        return state.runId()
                .map(activeConsumers::get)
                .orElseThrow(() -> new IllegalStateException(
                        "No event consumer registered for runId in state. " +
                                "This usually means the state was not initialised with a runId or the consumer was already removed."));
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
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.GUARDRAIL_INPUT, true);

        boolean blocked = guardrailChecker.checkInput(state.userMessage()).isPresent();
        if (blocked) {
            log.warn("[{}] Input guardrail blocked user message — matched blocklist term", agentProperties.id());
        }

        emit(events, StageId.GUARDRAIL_INPUT, false);
        return Map.of(WorkflowContext.KEY_GUARDRAIL_BLOCKED, blocked);
    }

    protected Map<String, Object> persistUserMessage(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.PERSIST_USER_MESSAGE, true);
        state.conversationId().ifPresent(id ->
                messageStorage.save(id, agentProperties.id(), new ConversationMessage(ConversationMessageRole.USER, state.userMessage(), state.guardrailPassed())));
        emit(events, StageId.PERSIST_USER_MESSAGE, false);
        log.debug("[{}] User message persisted for conversation [{}]", agentProperties.id(), state.conversationId().orElse(null));
        return Map.of();
    }

    protected Map<String, Object> loadMemory(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.LOAD_MEMORY, true);

        String memoryContext = "";
        if (agentProperties.memoryEnabled() && state.conversationId().isPresent()) {
            memoryContext = agentMemoryStorage
                    .getMemory(state.conversationId().get(), agentProperties.id())
                    .orElse("");
            log.debug("[{}] Loaded compact memory ({} chars)", agentProperties.id(), memoryContext.length());
        }

        emit(events, StageId.LOAD_MEMORY, false);
        return Map.of(WorkflowContext.KEY_MEMORY_CONTEXT, memoryContext);
    }

    protected Map<String, Object> classify(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.CLASSIFICATION, true);

        RoutingDecision decision = performClassification(state);

        emit(events, StageId.CLASSIFICATION, false);
        events.accept(new PipelineEvent.DecisionMade(decision.decisionMode(), decision.reason()));
        log.debug("[{}] Classification result: {} — {}", agentProperties.id(), decision.decisionMode(), decision.reason());

        return Map.of(WorkflowContext.KEY_ROUTING_DECISION, decision);
    }

    protected Map<String, Object> executeWorkflow(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.EXECUTE_WORKFLOW, true);

        LLMRequest request = new LLMRequest(
                agentProperties.model(),
                agentProperties.temperature(),
                withResponseContractInstructions(state.systemPrompt(), agentProperties.workflowPolicyProperties().responseContract()),
                state.userMessage(),
                state.memoryContext());

        log.debug("[{}] Starting LLM call with model [{}]", agentProperties.id(), agentProperties.model());
        // Buffered, not streamed live. Note this is only a *draft* — SELF_VERIFICATION decides
        // whether it's final or needs a retry, so guardrail-check/persist/stream must not happen
        // here. That single, exactly-once step lives in self-Verify (see applyOutputGuardrail).
        String response = llmProviderRegistry.get(agentProperties.llmProviderId()).stream(request, _ -> { });

        ValidationResult validation = responseValidator
                .validate(agentProperties.workflowPolicyProperties().responseContract(), response);
        if (!validation.valid()) {
            log.warn("[{}] Generated response failed validation: {}", agentProperties.id(), validation.reason());
        }

        emit(events, StageId.EXECUTE_WORKFLOW, false);
        log.debug("[{}] LLM streaming complete, length: {}", agentProperties.id(), response.length());

        return Map.of(
                WorkflowContext.KEY_GENERATED_RESPONSE, response,
                WorkflowContext.KEY_VALIDATION_PASSED, validation.valid(),
                WorkflowContext.KEY_VALIDATION_FAILURE_REASON, validation.reason() == null ? "" : validation.reason()
        );
    }

    protected Map<String, Object> generateClarification(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.GENERATE_CLARIFICATION, true);

        String clarification = state.routingDecision()
                .map(RoutingDecision::clarificationQuestion)
                .filter(q -> !q.isBlank())
                .orElseGet(() -> {
                    log.debug("[{}] No clarification question from classifier, generating via LLM", agentProperties.id());
                    return generateClarificationViaLlm(state);
                });

        emit(events, StageId.GENERATE_CLARIFICATION, false);
        String safeClarification = applyOutputGuardrail(state, clarification);
        log.debug("[{}] Clarification question generated", agentProperties.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeClarification);
    }

    protected Map<String, Object> generateRedirect(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.GENERATE_REDIRECT, true);

        String redirect = streamDecisionResponse(state, redirectPrompt(
                state.systemPrompt(), agentProperties.workflowPolicyProperties(),
                state.routingDecision().orElse(RoutingDecision.redirect("Redirecting mixed-scope request", state.userMessage()))));

        emit(events, StageId.GENERATE_REDIRECT, false);
        String safeRedirect = applyOutputGuardrail(state, redirect);
        log.debug("[{}] Redirect response sent", agentProperties.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRedirect);
    }

    protected Map<String, Object> generateGreeting(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.GENERATE_GREETING, true);

        RoutingDecision decision = state.routingDecision()
                .orElse(RoutingDecision.greet("Greeting", state.userMessage()));
        String greeting = streamDecisionResponse(state, greetingPrompt(
                state.systemPrompt(), agentProperties.workflowPolicyProperties(), decision));

        emit(events, StageId.GENERATE_GREETING, false);
        String safeGreeting = applyOutputGuardrail(state, greeting);
        events.accept(new PipelineEvent.ResponseCompleted(greeting));
        log.debug("[{}] Greeting response sent", agentProperties.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeGreeting);

    }

    protected Map<String, Object> generateRefusal(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.GENERATE_REFUSAL, true);

        String refusal = streamDecisionResponse(state, refusalPrompt(
                state.systemPrompt(), agentProperties.workflowPolicyProperties(),
                state.routingDecision().orElse(RoutingDecision.refuse("Refusing request", state.userMessage()))));

        emit(events, StageId.GENERATE_REFUSAL, false);
        String safeRefusal = applyOutputGuardrail(state, refusal);
        log.debug("[{}] Refusal response sent", agentProperties.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRefusal);

    }

    protected Map<String, Object> selfVerify(WorkflowContext state) {
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.SELF_VERIFICATION, true);

        if (state.validationPassed()) {
            emit(events, StageId.SELF_VERIFICATION, false);
            String safeResponse = applyOutputGuardrail(state, state.generatedResponse().orElse(""));
            return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeResponse, WorkflowContext.KEY_VALIDATION_PASSED, true);
        }

        if (state.retried()) {
            log.warn("[{}] Self-verification failed twice ({}) — returning best effort",
                    agentProperties.id(), state.validationFailureReason());
            events.accept(new PipelineEvent.StageFailed(StageId.SELF_VERIFICATION,
                    labelProvider.failed(StageId.SELF_VERIFICATION),
                    "Response still invalid after retry (%s) — returning best effort".formatted(state.validationFailureReason())));
            String safeRetried = applyOutputGuardrail(state, state.generatedResponse().orElse(""));
            return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRetried, WorkflowContext.KEY_VALIDATION_PASSED, true);
        }

        log.debug("[{}] Self-verification failed ({}) — attempting one retry",
                agentProperties.id(), state.validationFailureReason());

        String retryPrompt = retryPrompt(state.userMessage(), state.generatedResponse().orElse(""), state.validationFailureReason());
        var retryRequest = new LLMRequest(
                agentProperties.model(), agentProperties.temperature(),
                withResponseContractInstructions(state.systemPrompt(), agentProperties.workflowPolicyProperties().responseContract()),
                retryPrompt, state.memoryContext());

        // Buffered for the same reason as executeWorkflow: this must be the complete retry text
        // before it can be re-validated below.
        String retryResponse = llmProviderRegistry.get(agentProperties.llmProviderId()).stream(retryRequest, _ -> { });

        ValidationResult retryValidation = responseValidator
                .validate(agentProperties.workflowPolicyProperties().responseContract(), retryResponse);

        if (retryValidation.valid()) {
            log.debug("[{}] Retry passed validation", agentProperties.id());
            emit(events, StageId.SELF_VERIFICATION, false);
            String safeRetry = applyOutputGuardrail(state, retryResponse);
            return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRetry,
                    WorkflowContext.KEY_VALIDATION_PASSED, true,
                    WorkflowContext.KEY_RETRIED, true);
        }

        log.warn("[{}] Retry still invalid ({}) — returning latest retry result", agentProperties.id(), retryValidation.reason());
        events.accept(new PipelineEvent.StageFailed(
                StageId.SELF_VERIFICATION,
                labelProvider.failed(StageId.SELF_VERIFICATION),
                "Retry still invalid (%s) — returning latest retry result".formatted(retryValidation.reason())));

        String safeRetried = applyOutputGuardrail(state, retryResponse);
        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, safeRetried,
                WorkflowContext.KEY_VALIDATION_PASSED, true,
                WorkflowContext.KEY_RETRIED, true);
    }

    protected Map<String, Object> compactMemory(WorkflowContext state) {
        if (!agentProperties.memoryEnabled() || state.conversationId().isEmpty()) {
            return Map.of();
        }

        UUID conversationId = state.conversationId().get();
        String previousMemory = state.memoryContext();
        String userMessage = state.userMessage();
        String response = state.generatedResponse().orElse("");

        Thread.startVirtualThread(() -> compactMemoryAsync(conversationId, previousMemory, userMessage, response));
        return Map.of();
    }

    protected Map<String, Object> complete(WorkflowContext state) {
        consumer(state).accept(new PipelineEvent.ConversationCompleted());
        ConversationMessage message = new ConversationMessage(ConversationMessageRole.AGENT,
                state.generatedResponse().orElse(""), state.guardrailPassed());
        state.conversationId().ifPresent(id -> notificationChannels.forEach(
                channel -> channel.notify(agentProperties.id(), id, message)));
        log.debug("[{}] Pipeline complete for conversation [{}]", agentProperties.id(), state.conversationId().orElse(null));
        return Map.of();
    }

    // ── Classification ─────────────────────────────────────────────────────

    private RoutingDecision performClassification(WorkflowContext state) {
        String classificationPrompt = classificationPrompt(agentProperties.id(), agentProperties.workflowPolicyProperties(), state.userMessage());

        StageProperties stageProperties = stagePropertiesMap.get(StageId.CLASSIFICATION);
        LLLMProvider stageLLMProvider = llmProviderRegistry.get(stageProperties.llmProviderId());

        LLMRequest classifyRequest = new LLMRequest(
                stageProperties.model(), 0.1,
                CLASSIFICATION_SYSTEM_PROMPT,
                classificationPrompt,
                "");
        try {
            String jsonResponse = stageLLMProvider.call(classifyRequest);
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
        Consumer<PipelineEvent> events = consumer(state);
        emit(events, StageId.GUARDRAIL_OUTPUT, true);

        String finalResponse = guardrailChecker.checkOutput(candidateResponse)
                .map(_ -> {
                    log.warn("[{}] Output guardrail blocked generated response — matched blocklist term",
                            agentProperties.id());
                    return GUARDRAIL_FALLBACK_MESSAGE;
                })
                .orElse(candidateResponse);

        emit(events, StageId.GUARDRAIL_OUTPUT, false);

        // Persisted before a single token reaches the client. If the SSE connection drops for any
        // reason during the loop below, the response is already durably saved — reopening the
        // conversation shows it regardless of whether the client received any of the stream.
        emit(events, StageId.PERSIST_RESPONSE, true);
        state.conversationId().ifPresent(id -> messageStorage.save(
                id, agentProperties.id(), new ConversationMessage(ConversationMessageRole.AGENT, finalResponse, false)));
        emit(events, StageId.PERSIST_RESPONSE, false);

        for (String chunk : finalResponse.split("(?<=\\s)")) {
            events.accept(new PipelineEvent.Token(chunk));
        }
        events.accept(new PipelineEvent.ResponseCompleted(finalResponse));
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

    private void emit(Consumer<PipelineEvent> events, StageId stageId, boolean started) {
        if (started) {
            events.accept(new PipelineEvent.StageStarted(stageId, labelProvider.started(stageId)));
        } else {
            events.accept(new PipelineEvent.StageCompleted(stageId, labelProvider.completed(stageId)));
        }
    }

    private String generateClarificationViaLlm(WorkflowContext state) {
        StageProperties stageProperties = stagePropertiesMap.get(StageId.CLASSIFICATION);
        LLLMProvider stageLLMProvider = llmProviderRegistry.get(stageProperties.llmProviderId());

        String prompt = clarificationPrompt(state.userMessage());
        LLMRequest request = new LLMRequest(stageProperties.model(), stageProperties.temperature(), state.systemPrompt(), prompt, state.memoryContext());
        return stageLLMProvider.call(request);
    }

    private String streamDecisionResponse(WorkflowContext state, String prompt) {
        StageProperties stageProperties = stagePropertiesMap.get(StageId.CLASSIFICATION);
        LLLMProvider stageLLMProvider = llmProviderRegistry.get(stageProperties.llmProviderId());

        try {
            LLMRequest request = new LLMRequest(stageProperties.model(), agentProperties.temperature(), state.systemPrompt(), prompt, state.memoryContext());
            return stageLLMProvider.stream(request, token -> consumer(state).accept(new PipelineEvent.Token(token)));
        } catch (Exception ex) {
            log.warn("[{}] Decision response generation failed, using fallback: {}", agentProperties.id(), ex.getMessage());
            String fallback = agentProperties.workflowPolicyProperties().failedToProcessMessage();
            for (String token : fallback.split("(?<=\\s)")) {
                consumer(state).accept(new PipelineEvent.Token(token));
            }
            return fallback;
        }
    }

    private void compactMemoryAsync(UUID conversationId, String previousMemory, String userMessage, String response) {
        if (response.isBlank()) {
            return;
        }

        StageProperties stageProperties = stagePropertiesMap.get(StageId.CLASSIFICATION);
        LLLMProvider stageLLMProvider = llmProviderRegistry.get(stageProperties.llmProviderId());

        try {
            String prompt = memoryCompactionPrompt(previousMemory, userMessage, response);
            LLMRequest request = new LLMRequest(stageProperties.model(), 0.2, agentProperties.systemPrompt(), prompt, previousMemory);
            String compacted = stageLLMProvider.call(request);
            if (!compacted.isBlank()) {
                agentMemoryStorage.replace(conversationId, agentProperties.id(), compacted);
                log.debug("[{}] Memory compacted for conversation [{}]", agentProperties.id(), conversationId);
            }
        } catch (Exception ex) {
            log.warn("[{}] Memory compaction failed for conversation [{}]: {}", agentProperties.id(), conversationId, ex.getMessage());
        }
    }
}