package io.workflowai.adapter.out.runtime.graph;

import io.workflowai.application.execution.WorkflowState;
import io.workflowai.application.execution.stage.WorkflowStage;
import io.workflowai.application.port.out.WorkflowExecutor;
import io.workflowai.application.port.out.WorkflowExecutorFactory;
import io.workflowai.domain.exceptions.WorkflowBuildException;
import io.workflowai.domain.exceptions.WorkflowStageException;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowId;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LangGraph4jWorkflowExecutorFactory implements WorkflowExecutorFactory {

    private static final Logger log = LoggerFactory.getLogger(LangGraph4jWorkflowExecutorFactory.class);

    private final Map<StageId, WorkflowStage> stages;

    public LangGraph4jWorkflowExecutorFactory(List<WorkflowStage> stages) {
        this.stages = stages.stream().collect(Collectors.toMap(WorkflowStage::stageId, Function.identity()));
    }

    @Override
    public WorkflowExecutor build(WorkflowId workflowId) {
        CompiledGraph<LangGraph4jState> graph = switch (workflowId) {
            case STANDARD -> buildStandardWorkflowGraph();
            // more workflow variants can be added here
            default -> throw new WorkflowBuildException("Unsupported workflow variant: %s".formatted(workflowId));
        };
        return new LangGraph4jWorkflowExecutor(graph);
    }

    private CompiledGraph<LangGraph4jState> buildStandardWorkflowGraph() {
        try {
            StateGraph<LangGraph4jState> stateGraph = new StateGraph<>(LangGraph4jState.SCHEMA);

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
    private void wireStandardWorkflowNodes(StateGraph<LangGraph4jState> graph) {
        try {
            graph.addEdge(StateGraph.START, StageId.PERSIST_USER_MESSAGE.name());
            graph.addEdge(StageId.PERSIST_USER_MESSAGE.name(), StageId.LOAD_MEMORY.name());

            graph.addConditionalEdges(
                    StageId.LOAD_MEMORY.name(),
                    AsyncEdgeAction.edge_async(ctx -> new WorkflowState(ctx.data()).triggerSource().name()),
                    Map.of(
                            "USER_MESSAGE", StageId.CLASSIFICATION.name(),
                            "SYSTEM_TRIGGER", StageId.EXECUTE_WORKFLOW.name()
                    )
            );

            graph.addConditionalEdges(
                    StageId.CLASSIFICATION.name(),
                    AsyncEdgeAction.edge_async(ctx ->
                            new WorkflowState(ctx.data()).routingDecision()
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

    private AsyncNodeAction<LangGraph4jState> asyncNode(WorkflowStage stage) {
        NodeAction<LangGraph4jState> action = s -> {
            WorkflowState state = new WorkflowState(s.data());
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