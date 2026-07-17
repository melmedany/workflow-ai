package io.workflowai.application.pipeline;

import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.PipelineEvent;
import io.workflowai.domain.workflow.ResponseValidationPolicy;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowPrompts;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.domain.exceptions.ClassificationException;
import io.workflowai.domain.exceptions.WorkflowExecutionException;
import io.workflowai.domain.model.AgentConfig;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.domain.model.ConversationMessageRole;
import io.workflowai.domain.model.LlmRequest;
import io.workflowai.domain.model.RoutingDecision;
import io.workflowai.ports.outbound.AgentMemoryStoragePort;
import io.workflowai.ports.outbound.LlmProviderPort;
import io.workflowai.ports.outbound.MessageStoragePort;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class WorkflowPipeline {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPipeline.class);

    private final AgentConfig agentConfig;
    private final LlmProviderPort llmProvider;
    private final MessageStoragePort messageStoragePort;
    private final AgentMemoryStoragePort agentMemoryStoragePort;
    private final WorkflowPolicy policy;
    private final StageLabelProvider labelProvider;
    private final JsonMapper jsonMapper;
    private final CompiledGraph<WorkflowContext> graph;

    // Keyed by per-execution runId, NOT conversationId. Two concurrent invocations for the
    // same conversation (double-submit, caller-side retry, etc.) must not share a consumer —
    // keying by conversationId previously let one run's consumer be overwritten/removed by
    // another run of the same conversation, causing cross-talk or a NoSuchElement on consumer(state).
    // REQUIRES: WorkflowContext must expose a KEY_RUN_ID channel + a `UUID runId()` accessor,
    // populated the same way KEY_CONVERSATION_ID / conversationId() already are. Add there:
    //   String KEY_RUN_ID = "runId";
    //   UUID runId() { return (UUID) data().get(KEY_RUN_ID); } // always present, never optional
    private final ConcurrentHashMap<UUID, Consumer<PipelineEvent>> activeConsumers = new ConcurrentHashMap<>();

    public WorkflowPipeline(
            AgentConfig agentConfig,
            LlmProviderPort llmProvider,
            MessageStoragePort messageStoragePort,
            AgentMemoryStoragePort agentMemoryStoragePort,
            WorkflowPolicy policy,
            StageLabelProvider labelProvider,
            JsonMapper jsonMapper) {
        this.agentConfig = agentConfig;
        this.llmProvider = llmProvider;
        this.messageStoragePort = messageStoragePort;
        this.agentMemoryStoragePort = agentMemoryStoragePort;
        this.policy = policy;
        this.labelProvider = labelProvider;
        this.jsonMapper = jsonMapper;
        this.graph = buildGraph();
        log.info("WorkflowPipeline initialised for agent [{}]", agentConfig.id());
    }

    public void execute(AgentRequest request, Consumer<PipelineEvent> eventConsumer) {
        UUID conversationId = request.conversationId();
        UUID runId = UUID.randomUUID();
        log.info("Starting workflow execution for agent [{}], conversation [{}], run [{}]", agentConfig.id(), conversationId, runId);
        activeConsumers.put(runId, eventConsumer);
        Map<String, Object> initialState = Map.of(
                WorkflowContext.KEY_RUN_ID, runId,
                WorkflowContext.KEY_CONVERSATION_ID, conversationId,
                WorkflowContext.KEY_USER_MESSAGE, request.message(),
                WorkflowContext.KEY_SYSTEM_PROMPT, agentConfig.systemPrompt(),
                WorkflowContext.KEY_RETRIED, false,
                WorkflowContext.KEY_VALIDATION_PASSED, false
        );
        // TODO: no timeout/cancellation on graph.invoke() — a hung llmProvider call currently
        // blocks this thread indefinitely with no way to recover the run. Wrap with a bounded
        // executor + timeout (and ensure activeConsumers.remove(runId) still fires on timeout).
        try {
            graph.invoke(initialState);
            log.info("Workflow completed for agent [{}], conversation [{}], run [{}]", agentConfig.id(), conversationId, runId);
        } catch (WorkflowExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkflowExecutionException(agentConfig.id(),
                    "Unexpected pipeline failure: " + e.getMessage(), e);
        } finally {
            activeConsumers.remove(runId);
        }
    }

    private Consumer<PipelineEvent> consumer(WorkflowContext state) {
        return state.runId()
                .map(activeConsumers::get)
                .orElseThrow(() -> new IllegalStateException("No event consumer registered for this pipeline execution"));
    }

    // ── Graph construction ───────────────────────────────────────────────────

    private CompiledGraph<WorkflowContext> buildGraph() {
        try {
            return new StateGraph<>(WorkflowContext.SCHEMA)
                    .addNode(StageId.PERSIST_USER_MESSAGE.name(), AsyncNodeAction.node_async(this::persistUserMessage))
                    .addNode(StageId.LOAD_MEMORY.name(), AsyncNodeAction.node_async(this::loadMemory))
                    .addNode(StageId.CLASSIFICATION.name(), AsyncNodeAction.node_async(this::classify))
                    .addNode(StageId.EXECUTE_WORKFLOW.name(), AsyncNodeAction.node_async(this::executeWorkflow))
                    .addNode(StageId.GENERATE_CLARIFICATION.name(), AsyncNodeAction.node_async(this::generateClarification))
                    // (CLASSIFICATION_SYSTEM_PROMPT's schema never instructs the classifier to return it)
                    // and had a copy-paste bug emitting StageId.GENERATE_REDIRECT's completion event
                    // instead of its own.
                    .addNode(StageId.GENERATE_REDIRECT.name(), AsyncNodeAction.node_async(this::generateRedirect))
                    .addNode(StageId.APPLY_REFUSE.name(), AsyncNodeAction.node_async(this::applyRefuse))
                    .addNode(StageId.SELF_VERIFICATION.name(), AsyncNodeAction.node_async(this::selfVerify))
                    .addNode(StageId.PERSIST_RESPONSE.name(), AsyncNodeAction.node_async(this::persistResponse))
                    .addNode(StageId.PERSIST_MEMORY.name(), AsyncNodeAction.node_async(this::persistMemory))
                    .addNode(StageId.COMPLETE.name(), AsyncNodeAction.node_async(this::complete))
                    .addEdge(StateGraph.START, StageId.PERSIST_USER_MESSAGE.name())
                    .addEdge(StageId.PERSIST_USER_MESSAGE.name(), StageId.LOAD_MEMORY.name())
                    .addEdge(StageId.LOAD_MEMORY.name(), StageId.CLASSIFICATION.name())
                    .addConditionalEdges(StageId.CLASSIFICATION.name(),
                            AsyncEdgeAction.edge_async(this::routeDecision),
                            Map.of(
                                    DecisionMode.EXECUTE.name(), StageId.EXECUTE_WORKFLOW.name(),
                                    DecisionMode.CLARIFY.name(), StageId.GENERATE_CLARIFICATION.name(),
                                    DecisionMode.REDIRECT.name(), StageId.GENERATE_REDIRECT.name(),
                                    DecisionMode.REFUSE.name(), StageId.APPLY_REFUSE.name()))
                    .addEdge(StageId.EXECUTE_WORKFLOW.name(), StageId.SELF_VERIFICATION.name())
                    .addEdge(StageId.SELF_VERIFICATION.name(), StageId.PERSIST_RESPONSE.name())
                    .addEdge(StageId.GENERATE_CLARIFICATION.name(), StageId.PERSIST_RESPONSE.name())
                    .addEdge(StageId.GENERATE_REDIRECT.name(), StageId.PERSIST_RESPONSE.name())
                    .addEdge(StageId.APPLY_REFUSE.name(), StageId.PERSIST_RESPONSE.name())
                    .addEdge(StageId.PERSIST_RESPONSE.name(), StageId.PERSIST_MEMORY.name())
                    .addEdge(StageId.PERSIST_MEMORY.name(), StageId.COMPLETE.name())
                    .addEdge(StageId.COMPLETE.name(), StateGraph.END)
                    .compile();
        } catch (Exception e) {
            throw new WorkflowExecutionException(agentConfig.id(), "Failed to build workflow graph", e);
        }
    }

    // ── Nodes ────────────────────────────────────────────────────────────────

    private Map<String, Object> persistUserMessage(WorkflowContext state) {
        var events = consumer(state);
        emit(events, StageId.PERSIST_USER_MESSAGE, true);
        state.conversationId().ifPresent(id ->
                messageStoragePort.save(id, agentConfig.id(), new ConversationMessage(ConversationMessageRole.USER, state.userMessage())));
        emit(events, StageId.PERSIST_USER_MESSAGE, false);
        log.debug("[{}] User message persisted for conversation [{}]", agentConfig.id(), state.conversationId().orElse(null));
        return Map.of();
    }

    private Map<String, Object> loadMemory(WorkflowContext state) {
        var events = consumer(state);
        emit(events, StageId.LOAD_MEMORY, true);

        List<ConversationMessage> history = List.of();
        if (agentConfig.memoryEnabled() && state.conversationId().isPresent()) {
            var all = messageStoragePort.findByAgentIdAndConversationId(agentConfig.id(), state.conversationId().get());
            int window = agentConfig.memoryLimit() * 2;
            history = all.size() > window ? all.subList(all.size() - window, all.size()) : all;
            log.debug("[{}] Loaded {} history messages (window: {})", agentConfig.id(), history.size(), window);
        }

        emit(events, StageId.LOAD_MEMORY, false);
        return Map.of(WorkflowContext.KEY_HISTORY, history);
    }

    private Map<String, Object> classify(WorkflowContext state) {
        var events = consumer(state);
        emit(events, StageId.CLASSIFICATION, true);

        RoutingDecision decision = performClassification(state);

        emit(events, StageId.CLASSIFICATION, false);
        events.accept(new PipelineEvent.DecisionMade(decision.decisionMode(), decision.reason()));
        log.info("[{}] Classification result: {} — {}", agentConfig.id(), decision.decisionMode(), decision.reason());

        return Map.of(WorkflowContext.KEY_ROUTING_DECISION, decision);
    }

    private Map<String, Object> executeWorkflow(WorkflowContext state) {
        var events = consumer(state);
        emit(events, StageId.EXECUTE_WORKFLOW, true);

        var request = new LlmRequest(
                agentConfig.model(),
                agentConfig.temperature(),
                state.systemPrompt(),
                state.userMessage(),
                state.history());

        log.debug("[{}] Starting LLM stream with model [{}]", agentConfig.id(), agentConfig.model());
        String response = llmProvider.stream(request, token -> events.accept(new PipelineEvent.Token(token)));

        emit(events, StageId.EXECUTE_WORKFLOW, false);
        events.accept(new PipelineEvent.ResponseCompleted(response));
        log.debug("[{}] LLM streaming complete, response length: {}", agentConfig.id(), response.length());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, response);
    }

    private Map<String, Object> generateClarification(WorkflowContext state) {
        var events = consumer(state);
        emit(events, StageId.GENERATE_CLARIFICATION, true);

        String clarification = state.routingDecision()
                .map(RoutingDecision::clarificationQuestion)
                .filter(q -> q != null && !q.isBlank())
                .orElseGet(() -> {
                    log.debug("[{}] No clarification question from classifier, generating via LLM", agentConfig.id());
                    return generateClarificationViaLlm(state);
                });

        for (String token : clarification.split("(?<=\\s)")) {
            events.accept(new PipelineEvent.Token(token));
        }
        emit(events, StageId.GENERATE_CLARIFICATION, false);
        events.accept(new PipelineEvent.ResponseCompleted(clarification));
        log.info("[{}] Clarification question generated", agentConfig.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, clarification, WorkflowContext.KEY_VALIDATION_PASSED, true);
    }

    private Map<String, Object> generateRedirect(WorkflowContext state) {
        var events = consumer(state);
        emit(events, StageId.GENERATE_REDIRECT, true);

        String redirect = policy.redirectMessage();
        for (String token : redirect.split("(?<=\\s)")) {
            events.accept(new PipelineEvent.Token(token));
        }
        emit(events, StageId.GENERATE_REDIRECT, false);
        events.accept(new PipelineEvent.ResponseCompleted(redirect));
        log.info("[{}] Redirect response sent", agentConfig.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, redirect, WorkflowContext.KEY_VALIDATION_PASSED, true);
    }

    private Map<String, Object> applyRefuse(WorkflowContext state) {
        var events = consumer(state);
        emit(events, StageId.APPLY_REFUSE, true);

        String refusal = policy.refuseMessage();
        for (String token : refusal.split("(?<=\\s)")) {
            events.accept(new PipelineEvent.Token(token));
        }
        emit(events, StageId.APPLY_REFUSE, false);
        events.accept(new PipelineEvent.ResponseCompleted(refusal));
        log.info("[{}] Refusal response sent — no LLM tokens consumed", agentConfig.id());

        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, refusal, WorkflowContext.KEY_VALIDATION_PASSED, true);
    }

    // TODO(self-verification): this stage does not itself judge response quality — it only
    // reacts to WorkflowContext.KEY_VALIDATION_PASSED. Today nothing on the EXECUTE path ever
    // sets that flag true, so every successful EXECUTE turn falls through to the "not passed,
    // not retried yet" branch below and unconditionally spends a second LLM call redoing the
    // response — regardless of whether the first one was fine. This is a placeholder, not a
    // real verification step.
    //
    // Intended contract going forward: the agent-specific EXECUTE_WORKFLOW subgraph is
    // responsible for setting KEY_VALIDATION_PASSED based on its own internal check (a
    // critic call, a schema/format validator, whatever fits that agent). This generic node
    // should remain a safety-net retry-once, not the validator itself. Document this contract
    // wherever agent implementations are plugged in — an agent author who forgets to set the
    // flag will silently double their per-turn LLM cost.
    private Map<String, Object> selfVerify(WorkflowContext state) {
        if (ResponseValidationPolicy.acceptsCurrentResponse(state.validationPassed(), agentConfig.validationEnabled())) {
            emit(consumer(state), StageId.SELF_VERIFICATION, false);
            return Map.of(WorkflowContext.KEY_VALIDATION_PASSED, true);
        }
        if (state.retried()) {
            log.warn("[{}] Self-verification skipped — already retried once, returning best effort", agentConfig.id());
            String bestEffort = ResponseValidationPolicy.bestEffort(state.generatedResponse().orElse(""));
            return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, bestEffort, WorkflowContext.KEY_VALIDATION_PASSED, true);
        }

        var events = consumer(state);
        emit(events, StageId.SELF_VERIFICATION, true);
        log.info("[{}] Attempting self-verification retry", agentConfig.id());

        String retryPrompt = WorkflowPrompts.retryPrompt(state.userMessage(), state.generatedResponse().orElse(""));
        var retryRequest = new LlmRequest(
                agentConfig.model(), agentConfig.temperature(), state.systemPrompt(),
                retryPrompt, state.history());

        String retryResponse = llmProvider.stream(retryRequest,
                token -> events.accept(new PipelineEvent.Token(token)));

        log.warn("[{}] Self-verification retry still invalid — returning best effort", agentConfig.id());
        events.accept(new PipelineEvent.StageFailed(
                StageId.SELF_VERIFICATION,
                labelProvider.failed(StageId.SELF_VERIFICATION),
                "Retry still has issues — returning best effort"));
        String bestEffort = ResponseValidationPolicy.bestEffort(retryResponse);
        return Map.of(WorkflowContext.KEY_GENERATED_RESPONSE, bestEffort,
                WorkflowContext.KEY_VALIDATION_PASSED, true,
                WorkflowContext.KEY_RETRIED, true);
    }

    private Map<String, Object> persistResponse(WorkflowContext state) {
        var events = consumer(state);
        emit(events, StageId.PERSIST_RESPONSE, true);

        String response = state.generatedResponse().orElse("");
        state.conversationId().ifPresent(id ->
                messageStoragePort.save(id, agentConfig.id(), new ConversationMessage(ConversationMessageRole.AGENT, response)));

        emit(events, StageId.PERSIST_RESPONSE, false);
        log.debug("[{}] Response persisted, length: {}", agentConfig.id(), response.length());
        return Map.of();
    }

    private Map<String, Object> persistMemory(WorkflowContext state) {
        var events = consumer(state);

        if (!agentConfig.memoryEnabled() || state.conversationId().isEmpty()) {
            return Map.of();
        }

        emit(events, StageId.PERSIST_MEMORY, true);
        String response = state.generatedResponse().orElse("");
        if (!response.isBlank()) {
            agentMemoryStoragePort.add(state.conversationId().get(), agentConfig.id(), response);
            events.accept(new PipelineEvent.MemoryUpdated());
            log.debug("[{}] Memory updated for conversation [{}]", agentConfig.id(), state.conversationId().get());
        }
        emit(events, StageId.PERSIST_MEMORY, false);
        return Map.of();
    }

    private Map<String, Object> complete(WorkflowContext state) {
        consumer(state).accept(new PipelineEvent.ConversationCompleted());
        log.info("[{}] Pipeline complete for conversation [{}]",
                agentConfig.id(), state.conversationId().orElse(null));
        return Map.of();
    }

    // ── Routing ──────────────────────────────────────────────────────────────

    private String routeDecision(WorkflowContext state) {
        return state.routingDecision()
                .map(d -> d.decisionMode().name())
                .orElse("REFUSE");
    }

    // ── Classification ───────────────────────────────────────────────────────

    private RoutingDecision performClassification(WorkflowContext state) {
        String classificationPrompt = WorkflowPrompts.classificationPrompt(agentConfig.id(), policy, state.userMessage());

        var classifyRequest = new LlmRequest(
                agentConfig.model(), 0.1,
                WorkflowPrompts.CLASSIFICATION_SYSTEM_PROMPT,
                classificationPrompt,
                List.of());
        try {
            String jsonResponse = llmProvider.call(classifyRequest);
            return parseRoutingDecision(jsonResponse);
        } catch (ClassificationException e) {
            throw e;
        } catch (Exception e) {
            // Fail-closed: an unclassified request must not silently fall through to full
            // agent execution. This mirrors routeDecision()'s own REFUSE default when no
            // routing decision is present in the state — the two must-stay consistent.
            log.warn("[{}] Classification LLM call failed, defaulting to REFUSE: {}", agentConfig.id(), e.getMessage());
            return RoutingDecision.refuse(state.userMessage(), "Classification unavailable — failing closed: " + e.getMessage());
        }
    }

    private RoutingDecision parseRoutingDecision(String jsonResponse) {
        String json = extractJson(jsonResponse);
        try {
            return jsonMapper.readValue(json, RoutingDecision.class);
        } catch (Exception e) {
            throw new ClassificationException(agentConfig.id(), "Failed to parse routing decision from LLM response: " + jsonResponse, e);
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new ClassificationException(agentConfig.id(),
                    "No JSON object found in classification response: " + text);
        }
        return text.substring(start, end + 1);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void emit(Consumer<PipelineEvent> events, StageId stageId, boolean started) {
        if (started) {
            events.accept(new PipelineEvent.StageStarted(stageId, labelProvider.started(stageId)));
        } else {
            events.accept(new PipelineEvent.StageCompleted(stageId, labelProvider.completed(stageId)));
        }
    }

    private String generateClarificationViaLlm(WorkflowContext state) {
        String prompt = WorkflowPrompts.clarificationPrompt(state.userMessage());
        var request = new LlmRequest(agentConfig.model(), 0.7, state.systemPrompt(), prompt, state.history());
        return llmProvider.call(request);
    }
}