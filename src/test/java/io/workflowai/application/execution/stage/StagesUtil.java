package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.agent.TriggerSource;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.domain.workflow.WorkflowState;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.workflowai.application.execution.stage.StageSettings.StageSetting;
import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static io.workflowai.domain.workflow.StageId.CLASSIFICATION;

public class StagesUtil {

    protected static StageSettings stageSettings() {
        return new StageSettings(List.of(
                stageSetting(StageId.CLASSIFICATION, ChatProviderId.Ollama, "classification-model", 0.1),
                stageSetting(StageId.GENERATE_CLARIFICATION, ChatProviderId.Bonzai, "clarification-model", 0.3),
                stageSetting(StageId.GENERATE_REDIRECT, ChatProviderId.OpenAI, "redirect-model", 0.4),
                stageSetting(StageId.GENERATE_GREETING, ChatProviderId.Anthropic, "greeting-model", 0.5),
                stageSetting(StageId.GENERATE_REFUSAL, ChatProviderId.Bonzai, "refusal-model", 0.7),
                stageSetting(StageId.COMPACT_MEMORY, ChatProviderId.Ollama, "memory-model", 0.6)));
    }

    protected static WorkflowState state(RoutingDecision decision) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "request",
                WorkflowState.KEY_SYSTEM_PROMPT, "system",
                WorkflowState.KEY_MEMORY_CONTEXT, "memory",
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties(),
                WorkflowState.KEY_ROUTING_DECISION, decision,
                WorkflowState.KEY_GENERATED_RESPONSE, "response"));
    }

    protected static WorkflowState state(TriggerSource triggerSource, RoutingDecision decision) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "every day at 9am, summarize open PRs",
                WorkflowState.KEY_TRIGGER_SOURCE, triggerSource,
                WorkflowState.KEY_SCHEDULING_REQUESTED, true,
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties(),
                WorkflowState.KEY_ROUTING_DECISION, decision));
    }

    protected static WorkflowState state(String userMessage, boolean schedulingRequested) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, userMessage,
                WorkflowState.KEY_TRIGGER_SOURCE, TriggerSource.USER_MESSAGE,
                WorkflowState.KEY_SCHEDULING_REQUESTED, schedulingRequested,
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties()));
    }

    protected static ClassificationStage classificationStage(ChatProvider provider) {
        StageSettings settings = new StageSettings(List.of(
                new StageSetting(CLASSIFICATION, Ollama, "ollama-model", 0.1)));

        ChatProviderRegistry registry = new ChatProviderRegistry(List.of(provider));

        return new ClassificationStage(registry, settings, JsonMapper.builder().build(), List.of());
    }

    protected static AgentProperties agentProperties() {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                WorkflowId.STANDARD, ChatProviderId.Ollama, "agent-model", 0.9, "system", true,
                new WorkflowPolicy(List.of(), null, "fallback"));
    }

    protected static StageSetting stageSetting(StageId stageId, ChatProviderId providerId,
                                               String model, double temperature) {
        return new StageSetting(stageId, providerId, model, temperature);
    }

    protected static RoutingDecision decision(StageId stageId) {
        return switch (stageId) {
            case GENERATE_REDIRECT -> RoutingDecision.redirect("redirect", "request");
            case GENERATE_GREETING -> RoutingDecision.greet("greeting", "request");
            case GENERATE_REFUSAL -> RoutingDecision.refuse("refusal", "request");
            default -> throw new IllegalArgumentException("Unexpected decision stage: " + stageId);
        };
    }
}
