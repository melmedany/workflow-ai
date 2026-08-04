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
                    .addNode(StageId.PERSIST_USER_MESSAGE.name(), asyncNode(stages.get(StageId.PERSIST_USER_MESSAGE)))
                    .addNode(StageId.LOAD_MEMORY.name(), asyncNode(stages.get(StageId.LOAD_MEMORY)))
                    .addNode(StageId.CLASSIFICATION.name(), asyncNode(stages.get(StageId.CLASSIFICATION)))
                    .addNode(StageId.EXECUTE_WORKFLOW.name(), asyncNode(stages.get(StageId.EXECUTE_WORKFLOW)))
                    .addNode(StageId.GENERATE_CLARIFICATION.name(), asyncNode(stages.get(StageId.GENERATE_CLARIFICATION)))
                    .addNode(StageId.GENERATE_GREETING.name(), asyncNode(stages.get(StageId.GENERATE_GREETING)))
                    .addNode(StageId.GENERATE_REDIRECT.name(), asyncNode(stages.get(StageId.GENERATE_REDIRECT)))
                    .addNode(StageId.GENERATE_REFUSAL.name(), asyncNode(stages.get(StageId.GENERATE_REFUSAL)))
                    .addNode(StageId.SELF_VERIFICATION.name(), asyncNode(stages.get(StageId.SELF_VERIFICATION)))
                    .addNode(StageId.COMPACT_MEMORY.name(), asyncNode(stages.get(StageId.COMPACT_MEMORY)))
                    .addNode(StageId.COMPLETE.name(), asyncNode(stages.get(StageId.COMPLETE)));

            wireStandardWorkflowNodes(stateGraph);

            return stateGraph.compile();
        } catch (Exception ex) {
            throw new WorkflowBuildException("Failed to build workflow graph for workflow [%s]".formatted(WorkflowId.STANDARD), ex);
        }
    }

    /**
     * STANDARD: classification, self-verification, and memory compaction. No dedicated input-guardrail
     * node — input guardrailing happens inside the ChatProvider call that CLASSIFICATION/
     * EXECUTE_WORKFLOW makes.
     * <br>
     * START -> PERSIST_USER_MESSAGE -> LOAD_MEMORY -> (decision branches)
     * EXECUTE_WORKFLOW -> SELF_VERIFICATION -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_CLARIFICATION -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_GREETING -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_REDIRECT -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_REFUSAL -> COMPACT_MEMORY -> COMPLETE -> END
     */
    private void wireStandardWorkflowNodes(StateGraph<WorkflowState> graph) {
        try {
            graph.addEdge(StateGraph.START, StageId.PERSIST_USER_MESSAGE.name());
            graph.addEdge(StageId.PERSIST_USER_MESSAGE.name(), StageId.LOAD_MEMORY.name());

            graph.addConditionalEdges(
                    StageId.LOAD_MEMORY.name(),
                    AsyncEdgeAction.edge_async(state -> state.triggerSource().name()),
                    Map.of(
                            "USER_MESSAGE", StageId.CLASSIFICATION.name(),
                            "SYSTEM_TRIGGER", StageId.EXECUTE_WORKFLOW.name()
                    )
            );

            graph.addConditionalEdges(
                    StageId.CLASSIFICATION.name(),
                    AsyncEdgeAction.edge_async(state -> state.routingDecision()
                                    .map(d -> d.decisionMode().name())
                                    .orElse(DecisionMode.REFUSE.name())
                    ),
                    Map.of(
                            DecisionMode.EXECUTE.name(), StageId.EXECUTE_WORKFLOW.name(),
                            DecisionMode.CLARIFY.name(), StageId.GENERATE_CLARIFICATION.name(),
                            DecisionMode.GREET.name(), StageId.GENERATE_GREETING.name(),
                            DecisionMode.REDIRECT.name(), StageId.GENERATE_REDIRECT.name(),
                            DecisionMode.REFUSE.name(), StageId.GENERATE_REFUSAL.name()
                    )
            );

            graph.addEdge(StageId.EXECUTE_WORKFLOW.name(), StageId.SELF_VERIFICATION.name());
            graph.addEdge(StageId.SELF_VERIFICATION.name(), StageId.COMPACT_MEMORY.name());
            graph.addEdge(StageId.GENERATE_CLARIFICATION.name(), StageId.COMPACT_MEMORY.name());
            graph.addEdge(StageId.GENERATE_GREETING.name(), StageId.COMPACT_MEMORY.name());
            graph.addEdge(StageId.GENERATE_REDIRECT.name(), StageId.COMPACT_MEMORY.name());
            graph.addEdge(StageId.GENERATE_REFUSAL.name(), StageId.COMPACT_MEMORY.name());
            graph.addEdge(StageId.COMPACT_MEMORY.name(), StageId.COMPLETE.name());
            graph.addEdge(StageId.COMPLETE.name(), StateGraph.END);
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