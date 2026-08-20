package io.workflowai.domain.workflow;

import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.agent.TriggerSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowExecutorFactoryTest {

    @Test
    void executeDecisionWithoutSchedulingReachesCompleteThroughExecuteWorkflow() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited,
                Map.of(StageId.CLASSIFICATION, decisionOutput(executeDecision())),
                initialState(TriggerSource.USER_MESSAGE, false));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.CLASSIFICATION,
                StageId.EXECUTE_WORKFLOW, StageId.SELF_VERIFICATION, StageId.COMPACT_MEMORY, StageId.COMPLETE);
    }

    @Test
    void executeDecisionWithSchedulingReachesCompleteThroughCreateTask() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited,
                Map.of(StageId.CLASSIFICATION, decisionOutput(executeDecision())),
                initialState(TriggerSource.USER_MESSAGE, true));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.CLASSIFICATION,
                StageId.CREATE_TASK, StageId.COMPACT_MEMORY, StageId.COMPLETE);
    }

    @Test
    void clarifyDecisionReachesCompleteThroughGenerateClarification() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited,
                Map.of(StageId.CLASSIFICATION, decisionOutput(RoutingDecision.clarify("r", "i", "q?"))),
                initialState(TriggerSource.USER_MESSAGE, false));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.CLASSIFICATION,
                StageId.GENERATE_CLARIFICATION, StageId.COMPACT_MEMORY, StageId.COMPLETE);
    }

    @Test
    void greetDecisionReachesCompleteThroughGenerateGreeting() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited,
                Map.of(StageId.CLASSIFICATION, decisionOutput(RoutingDecision.greet("r", "i"))),
                initialState(TriggerSource.USER_MESSAGE, false));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.CLASSIFICATION,
                StageId.GENERATE_GREETING, StageId.COMPACT_MEMORY, StageId.COMPLETE);
    }

    @Test
    void redirectDecisionReachesCompleteThroughGenerateRedirect() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited,
                Map.of(StageId.CLASSIFICATION, decisionOutput(RoutingDecision.redirect("r", "i"))),
                initialState(TriggerSource.USER_MESSAGE, false));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.CLASSIFICATION,
                StageId.GENERATE_REDIRECT, StageId.COMPACT_MEMORY, StageId.COMPLETE);
    }

    @Test
    void refuseDecisionReachesCompleteThroughGenerateRefusal() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited,
                Map.of(StageId.CLASSIFICATION, decisionOutput(RoutingDecision.refuse("r", "i"))),
                initialState(TriggerSource.USER_MESSAGE, false));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.CLASSIFICATION,
                StageId.GENERATE_REFUSAL, StageId.COMPACT_MEMORY, StageId.COMPLETE);
    }

    @Test
    void systemTriggeredRunSkipsClassificationAndReachesComplete() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited, Map.of(), initialState(TriggerSource.SYSTEM_TRIGGER, false));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.EXECUTE_WORKFLOW,
                StageId.SELF_VERIFICATION, StageId.COMPACT_MEMORY, StageId.COMPLETE);
        assertThat(visited).doesNotContain(StageId.CLASSIFICATION);
    }

    @Test
    void createTaskClarifyOutcomeReachesCompleteThroughGenerateClarification() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited,
                Map.of(
                        StageId.CLASSIFICATION, decisionOutput(executeDecision()),
                        StageId.CREATE_TASK, decisionOutput(RoutingDecision.clarify("r", "i", "q?"))),
                initialState(TriggerSource.USER_MESSAGE, true));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.CLASSIFICATION,
                StageId.CREATE_TASK, StageId.GENERATE_CLARIFICATION, StageId.COMPACT_MEMORY, StageId.COMPLETE);
    }

    @Test
    void createTaskRefuseOutcomeReachesCompleteThroughGenerateRefusal() {
        List<StageId> visited = new CopyOnWriteArrayList<>();

        WorkflowExecutionResult result = run(visited,
                Map.of(
                        StageId.CLASSIFICATION, decisionOutput(executeDecision()),
                        StageId.CREATE_TASK, decisionOutput(RoutingDecision.refuse("r", "i"))),
                initialState(TriggerSource.USER_MESSAGE, true));

        assertThat(result.outcome()).isEqualTo(WorkflowOutcome.COMPLETED);
        assertThat(visited).containsExactly(
                StageId.PERSIST_USER_MESSAGE, StageId.LOAD_MEMORY, StageId.CLASSIFICATION,
                StageId.CREATE_TASK, StageId.GENERATE_REFUSAL, StageId.COMPACT_MEMORY, StageId.COMPLETE);
    }

    private WorkflowExecutionResult run(List<StageId> visited, Map<StageId, Function<WorkflowState, Map<String, Object>>> overrides,
                                        Map<String, Object> initialState) {
        List<WorkflowStage> stages = new ArrayList<>();
        for (StageId stageId : StageId.values()) {
            Function<WorkflowState, Map<String, Object>> output = overrides.getOrDefault(stageId, _ -> Map.of());
            stages.add(recordingStage(stageId, visited, output));
        }

        WorkflowExecutorFactory factory = new WorkflowExecutorFactory(stages);
        WorkflowExecutor executor = factory.build(WorkflowId.STANDARD);
        return executor.execute(initialState);
    }

    private static WorkflowStage recordingStage(StageId stageId, List<StageId> visited,
                                                Function<WorkflowState, Map<String, Object>> output) {
        return new WorkflowStage() {
            @Override
            public StageId stageId() {
                return stageId;
            }

            @Override
            public Map<String, Object> execute(WorkflowState state) {
                visited.add(stageId);
                return output.apply(state);
            }
        };
    }

    private static Function<WorkflowState, Map<String, Object>> decisionOutput(RoutingDecision decision) {
        return _ -> Map.of(WorkflowState.KEY_ROUTING_DECISION, decision);
    }

    private static RoutingDecision executeDecision() {
        return new RoutingDecision(DecisionMode.EXECUTE, List.of(), "do something", null, "in scope",
                null, null, null, null);
    }

    private Map<String, Object> initialState(TriggerSource triggerSource, boolean schedulingRequested) {
        Map<String, Object> state = new HashMap<>();
        state.put(WorkflowState.KEY_RUN_ID, UUID.randomUUID());
        state.put(WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID());
        state.put(WorkflowState.KEY_USER_MESSAGE, "hello");
        state.put(WorkflowState.KEY_TRIGGER_SOURCE, triggerSource);
        state.put(WorkflowState.KEY_SCHEDULING_REQUESTED, schedulingRequested);
        state.put(WorkflowState.KEY_AGENT_PROPERTIES, agentProperties());
        return state;
    }

    private AgentProperties agentProperties() {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                WorkflowId.STANDARD, ChatProviderId.Ollama, "agent-model", 0.5, "system", true,
                new WorkflowPolicy(List.of(), null, "fallback"));
    }
}
