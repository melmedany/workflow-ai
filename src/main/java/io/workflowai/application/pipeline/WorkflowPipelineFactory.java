package io.workflowai.application.pipeline;

import io.workflowai.application.GuardrailProperties;
import io.workflowai.application.LLMProviderRegistry;
import io.workflowai.application.StagesProperties;
import io.workflowai.domain.exceptions.WorkflowBuildException;
import io.workflowai.domain.model.AgentProperties;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.GuardrailChecker;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.ports.outbound.AgentMemoryStorage;
import io.workflowai.ports.outbound.MessageStorage;
import io.workflowai.ports.outbound.NotificationChannel;
import io.workflowai.ports.outbound.RunHistoryPort;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@Component
public class WorkflowPipelineFactory {

    private static final Logger log = LoggerFactory.getLogger(WorkflowPipelineFactory.class);

    private final StagesProperties stagesProperties;
    private final LLMProviderRegistry llmProviderRegistry;
    private final MessageStorage messageStorage;
    private final AgentMemoryStorage agentMemoryStorage;
    private final RunHistoryPort runHistoryPort;
    private final List<NotificationChannel> notificationChannels;
    private final GuardrailChecker guardrailChecker;
    private final JsonMapper jsonMapper;

    public WorkflowPipelineFactory(
            StagesProperties stagesProperties,
            LLMProviderRegistry llmProviderRegistry,
            MessageStorage messageStorage,
            AgentMemoryStorage agentMemoryStorage,
            RunHistoryPort runHistoryPort,
            List<NotificationChannel> notificationChannels,
            GuardrailProperties guardrailProperties,
            JsonMapper jsonMapper) {
        this.stagesProperties = stagesProperties;
        this.llmProviderRegistry = llmProviderRegistry;
        this.messageStorage = messageStorage;
        this.agentMemoryStorage = agentMemoryStorage;
        this.runHistoryPort = runHistoryPort;
        this.notificationChannels = notificationChannels;
        this.guardrailChecker = new GuardrailChecker(guardrailProperties);
        this.jsonMapper = jsonMapper;
    }

    public WorkflowPipeline build(
            WorkflowPipelineId pipelineId,
            AgentProperties agentProperties) {
        log.debug("Building [{}] workflow for agent [{}]", pipelineId, agentProperties.id());
        WorkflowPipeline pipeline = new WorkflowPipeline(
                agentProperties, llmProviderRegistry, stagesProperties,
                messageStorage, agentMemoryStorage, runHistoryPort,
                notificationChannels, guardrailChecker, jsonMapper
        );

        // Build and compile the graph, binding node actions to the pipeline instance
        // more graphs to be added in future
        CompiledGraph<WorkflowContext> graph = switch (pipelineId) {
            case STANDARD -> buildStandardPipelineGraph(pipeline, pipelineId);
            default -> throw new WorkflowBuildException("Unsupported pipeline variant: %s".formatted(pipelineId));
        };

        pipeline.setGraph(graph);

        return pipeline;
    }

    private CompiledGraph<WorkflowContext> buildStandardPipelineGraph(WorkflowPipeline pipeline, WorkflowPipelineId pipelineId) {
        try {
            StateGraph<WorkflowContext> stateGraph = new StateGraph<>(WorkflowContext.SCHEMA);

            stateGraph
                    .addNode(StageId.GUARDRAIL_INPUT.name(),
                            asyncNode(pipeline.stageAction(StageId.GUARDRAIL_INPUT, pipeline::guardrailInput)))
                    .addNode(StageId.PERSIST_USER_MESSAGE.name(),
                            asyncNode(pipeline.stageAction(StageId.PERSIST_USER_MESSAGE, pipeline::persistUserMessage)))
                    .addNode(StageId.LOAD_MEMORY.name(),
                            asyncNode(pipeline.stageAction(StageId.LOAD_MEMORY, pipeline::loadMemory)))
                    .addNode(StageId.CLASSIFICATION.name(),
                            asyncNode(pipeline.stageAction(StageId.CLASSIFICATION, pipeline::classify)))
                    .addNode(StageId.EXECUTE_WORKFLOW.name(),
                            asyncNode(pipeline.stageAction(StageId.EXECUTE_WORKFLOW, pipeline::executeWorkflow)))
                    .addNode(StageId.GENERATE_CLARIFICATION.name(),
                            asyncNode(pipeline.stageAction(StageId.GENERATE_CLARIFICATION, pipeline::generateClarification)))
                    .addNode(StageId.GENERATE_GREETING.name(),
                            asyncNode(pipeline.stageAction(StageId.GENERATE_GREETING, pipeline::generateGreeting)))
                    .addNode(StageId.GENERATE_REDIRECT.name(),
                            asyncNode(pipeline.stageAction(StageId.GENERATE_REDIRECT, pipeline::generateRedirect)))
                    .addNode(StageId.GENERATE_REFUSAL.name(),
                            asyncNode(pipeline.stageAction(StageId.GENERATE_REFUSAL, pipeline::generateRefusal)))
                    .addNode(StageId.SELF_VERIFICATION.name(),
                            asyncNode(pipeline.stageAction(StageId.SELF_VERIFICATION, pipeline::selfVerify)))
                    .addNode(StageId.COMPACT_MEMORY.name(),
                            asyncNode(pipeline.stageAction(StageId.COMPACT_MEMORY, pipeline::compactMemory)))
                    .addNode(StageId.COMPLETE.name(),
                            asyncNode(pipeline.stageAction(StageId.COMPLETE, pipeline::complete)));

            wireStandardPipeline(stateGraph);

            return stateGraph.compile();
        } catch (Exception ex) {
            throw new WorkflowBuildException("Failed to build workflow graph for pipeline [%s]".formatted(pipelineId), ex);
        }
    }

    // ── Pipeline Topologies ─────────────────────────────────────────────────

    /**
     * STANDARD: Full pipeline with guardrails, classification, self-verification, and memory compaction.
     * <br>
     * START -> GUARDRAIL_INPUT -> PERSIST_USER_MESSAGE -> LOAD_MEMORY -> CLASSIFICATION -> (decision branches)
     * EXECUTE_WORKFLOW -> SELF_VERIFICATION -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_CLARIFICATION -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_GREETING -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_REDIRECT -> COMPACT_MEMORY -> COMPLETE -> END
     * GENERATE_REFUSAL -> COMPACT_MEMORY -> COMPLETE -> END
     */
    private void wireStandardPipeline(StateGraph<WorkflowContext> graph) {
        try {
            graph.addEdge(StateGraph.START, StageId.GUARDRAIL_INPUT.name());
            graph.addEdge(StageId.GUARDRAIL_INPUT.name(), StageId.PERSIST_USER_MESSAGE.name());
            graph.addConditionalEdges(
                    StageId.PERSIST_USER_MESSAGE.name(),
                    AsyncEdgeAction.edge_async(ctx -> ctx.guardrailBlocked() ? "BLOCKED" : "CLEAN"),
                    Map.of(
                            "BLOCKED", StageId.GENERATE_REFUSAL.name(),
                            "CLEAN", StageId.LOAD_MEMORY.name()));

            graph.addConditionalEdges(
                    StageId.LOAD_MEMORY.name(),
                    AsyncEdgeAction.edge_async(ctx -> ctx.triggerSource().name()),
                    Map.of(
                            "USER_MESSAGE", StageId.CLASSIFICATION.name(),
                            "SYSTEM_TRIGGER", StageId.EXECUTE_WORKFLOW.name()
                    )
            );

            graph.addConditionalEdges(
                    StageId.CLASSIFICATION.name(),
                    AsyncEdgeAction.edge_async(ctx ->
                            ctx.routingDecision()
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

    // ── Node Action Helpers ─────────────────────────────────────────────────

    private AsyncNodeAction<WorkflowContext> asyncNode(NodeAction<WorkflowContext> action) {
        return AsyncNodeAction.node_async(action);
    }
}