package io.workflowai.domain.workflow;

import io.workflowai.domain.exceptions.WorkflowBuildException;
import io.workflowai.domain.exceptions.WorkflowStageException;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentStateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.workflowai.domain.agent.TriggerSource.SYSTEM_TRIGGER;
import static io.workflowai.domain.agent.TriggerSource.USER_MESSAGE;
import static io.workflowai.domain.workflow.DecisionMode.CLARIFY;
import static io.workflowai.domain.workflow.DecisionMode.EXECUTE;
import static io.workflowai.domain.workflow.DecisionMode.EXECUTE_SCHEDULE;
import static io.workflowai.domain.workflow.DecisionMode.GREET;
import static io.workflowai.domain.workflow.DecisionMode.REDIRECT;
import static io.workflowai.domain.workflow.DecisionMode.REFUSE;
import static io.workflowai.domain.workflow.StageId.CLASSIFICATION;
import static io.workflowai.domain.workflow.StageId.COMPACT_MEMORY;
import static io.workflowai.domain.workflow.StageId.COMPLETE;
import static io.workflowai.domain.workflow.StageId.CREATE_TASK;
import static io.workflowai.domain.workflow.StageId.EXECUTE_WORKFLOW;
import static io.workflowai.domain.workflow.StageId.GENERATE_CLARIFICATION;
import static io.workflowai.domain.workflow.StageId.GENERATE_GREETING;
import static io.workflowai.domain.workflow.StageId.GENERATE_REDIRECT;
import static io.workflowai.domain.workflow.StageId.GENERATE_REFUSAL;
import static io.workflowai.domain.workflow.StageId.LOAD_MEMORY;
import static io.workflowai.domain.workflow.StageId.PERSIST_USER_MESSAGE;
import static io.workflowai.domain.workflow.StageId.SELF_VERIFICATION;

/**
 * Builds a {@link WorkflowExecutor} for a given workflow.
 */
public class WorkflowExecutorFactory {

    private static final Logger log = LoggerFactory.getLogger(WorkflowExecutorFactory.class);

    static final AgentStateFactory<WorkflowState> SCHEMA = WorkflowState::new;

    // keep in sync with the switch in build(WorkflowId)
    private static final Set<WorkflowId> SUPPORTED = EnumSet.of(WorkflowId.STANDARD);

    private final Map<StageId, WorkflowStage> stages;

    public WorkflowExecutorFactory(List<WorkflowStage> stages) {
        this.stages = stages.stream().collect(Collectors.toMap(WorkflowStage::stageId, Function.identity()));
    }

    public boolean isSupported(WorkflowId workflowId) {
        return SUPPORTED.contains(workflowId);
    }

    public WorkflowExecutor build(WorkflowId workflowId) {
        CompiledGraph<WorkflowState> graph = switch (workflowId) {
            case STANDARD -> buildStandardWorkflowGraph();
            // more workflow variants can be mapped here
            default -> throw new WorkflowBuildException("Unsupported workflow variant: %s".formatted(workflowId));
        };
        return new WorkflowExecutor(graph);
    }

    private CompiledGraph<WorkflowState> buildStandardWorkflowGraph() {
        try {
            StateGraph<WorkflowState> stateGraph = new StateGraph<>(SCHEMA);

            stateGraph
                    .addNode(PERSIST_USER_MESSAGE.name(), asyncNode(stages.get(PERSIST_USER_MESSAGE)))
                    .addNode(LOAD_MEMORY.name(), asyncNode(stages.get(LOAD_MEMORY)))
                    .addNode(CLASSIFICATION.name(), asyncNode(stages.get(CLASSIFICATION)))
                    .addNode(EXECUTE_WORKFLOW.name(), asyncNode(stages.get(EXECUTE_WORKFLOW)))
                    .addNode(CREATE_TASK.name(), asyncNode(stages.get(CREATE_TASK)))
                    .addNode(GENERATE_CLARIFICATION.name(), asyncNode(stages.get(GENERATE_CLARIFICATION)))
                    .addNode(GENERATE_GREETING.name(), asyncNode(stages.get(GENERATE_GREETING)))
                    .addNode(GENERATE_REDIRECT.name(), asyncNode(stages.get(GENERATE_REDIRECT)))
                    .addNode(GENERATE_REFUSAL.name(), asyncNode(stages.get(GENERATE_REFUSAL)))
                    .addNode(SELF_VERIFICATION.name(), asyncNode(stages.get(SELF_VERIFICATION)))
                    .addNode(COMPACT_MEMORY.name(), asyncNode(stages.get(COMPACT_MEMORY)))
                    .addNode(COMPLETE.name(), asyncNode(stages.get(COMPLETE)));

            wireStandardWorkflowNodes(stateGraph);

            return stateGraph.compile();
        } catch (Exception ex) {
            throw new WorkflowBuildException("Failed to build workflow graph for workflow [%s]".formatted(WorkflowId.STANDARD), ex);
        }
    }

    /**
     * STANDARD: classification, self-verification, and memory compaction. No dedicated input-guardrail
     * node input guardrailing happens inside the ChatProvider call that CLASSIFICATION/
     * EXECUTE_WORKFLOW makes.
     * <br>
     * START -> PERSIST_USER_MESSAGE -> LOAD_MEMORY -> (decision branches)
     * EXECUTE_WORKFLOW -> SELF_VERIFICATION -> COMPACT_MEMORY -> COMPLETE -> END
     * CREATE_TASK -> (CLARIFY/REFUSE re-route, or straight to) COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_CLARIFICATION -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_GREETING -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_REDIRECT -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_REFUSAL -> COMPACT_MEMORY -> COMPLETE -> END
     */
    private void wireStandardWorkflowNodes(StateGraph<WorkflowState> graph) {
        try {
            graph.addEdge(StateGraph.START, PERSIST_USER_MESSAGE.name());
            graph.addEdge(PERSIST_USER_MESSAGE.name(), LOAD_MEMORY.name());

            graph.addConditionalEdges(
                    LOAD_MEMORY.name(),
                    AsyncEdgeAction.edge_async(state -> state.triggerSource().name()),
                    Map.of(
                            USER_MESSAGE.name(), CLASSIFICATION.name(),
                            SYSTEM_TRIGGER.name(), EXECUTE_WORKFLOW.name()
                    )
            );

            graph.addConditionalEdges(
                    CLASSIFICATION.name(),
                    AsyncEdgeAction.edge_async(state -> {
                        DecisionMode mode = state.routingDecision()
                                .map(RoutingDecision::decisionMode)
                                .orElse(REFUSE);
                        if (mode == EXECUTE && state.schedulingRequested()) {
                            return EXECUTE_SCHEDULE.name();
                        }
                        return mode.name();
                    }),
                    Map.of(
                            EXECUTE.name(), EXECUTE_WORKFLOW.name(),
                            EXECUTE_SCHEDULE.name(), CREATE_TASK.name(),
                            CLARIFY.name(), GENERATE_CLARIFICATION.name(),
                            GREET.name(), GENERATE_GREETING.name(),
                            REDIRECT.name(), GENERATE_REDIRECT.name(),
                            REFUSE.name(), GENERATE_REFUSAL.name()
                    )
            );

            graph.addConditionalEdges(
                    CREATE_TASK.name(),
                    AsyncEdgeAction.edge_async(state -> state.routingDecision()
                                    .map(d -> d.decisionMode().name())
                                    .orElse(EXECUTE.name())
                    ),
                    Map.of(
                            EXECUTE.name(), COMPACT_MEMORY.name(),
                            CLARIFY.name(), GENERATE_CLARIFICATION.name(),
                            REFUSE.name(), GENERATE_REFUSAL.name()
                    )
            );

            graph.addEdge(EXECUTE_WORKFLOW.name(), SELF_VERIFICATION.name());
            graph.addEdge(SELF_VERIFICATION.name(), COMPACT_MEMORY.name());
            graph.addEdge(GENERATE_CLARIFICATION.name(), COMPACT_MEMORY.name());
            graph.addEdge(GENERATE_GREETING.name(), COMPACT_MEMORY.name());
            graph.addEdge(GENERATE_REDIRECT.name(), COMPACT_MEMORY.name());
            graph.addEdge(GENERATE_REFUSAL.name(), COMPACT_MEMORY.name());
            graph.addEdge(COMPACT_MEMORY.name(), COMPLETE.name());
            graph.addEdge(COMPLETE.name(), StateGraph.END);
        } catch (Exception ex) {
            throw new WorkflowBuildException("Failed to build workflow graph", ex);
        }
    }

    private AsyncNodeAction<WorkflowState> asyncNode(WorkflowStage stage) {
        NodeAction<WorkflowState> action = state -> {
            try {
                return stage.execute(state);
            } catch (WorkflowStageException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("Stage [{}] failed: {}", stage.stageId(), ex.getMessage());
                throw new WorkflowStageException(state.agentProperties().id(), stage.stageId(), ex.getMessage(), ex);
            }
        };
        return AsyncNodeAction.node_async(action);
    }
}