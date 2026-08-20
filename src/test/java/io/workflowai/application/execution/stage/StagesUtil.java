package io.workflowai.application.execution.stage;

import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.agent.TriggerSource;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.domain.workflow.response.ResponseContract;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StagesUtil {

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

    protected static WorkflowState state(boolean validationPassed, boolean retried, String generatedResponse,
                                         ResponseContract responseContract) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "what's the weather",
                WorkflowState.KEY_SYSTEM_PROMPT, "system",
                WorkflowState.KEY_MEMORY_CONTEXT, "",
                WorkflowState.KEY_GENERATED_RESPONSE, generatedResponse,
                WorkflowState.KEY_VALIDATION_PASSED, validationPassed,
                WorkflowState.KEY_VALIDATION_FAILURE_REASON, "too short",
                WorkflowState.KEY_RETRIED, retried,
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties(responseContract)));
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

    protected static WorkflowState state(String generatedResponse) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_GENERATED_RESPONSE, generatedResponse,
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties()));
    }

    protected static WorkflowState state(TriggerSource triggerSource, String userMessage) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, userMessage,
                WorkflowState.KEY_TRIGGER_SOURCE, triggerSource,
                WorkflowState.KEY_AGENT_PROPERTIES, StagesUtil.agentProperties()));
    }

    protected static WorkflowState state(boolean memoryEnabled) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties(memoryEnabled)));
    }

    protected static WorkflowState state(boolean memoryEnabled, String userMessage, String generatedResponse) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, userMessage,
                WorkflowState.KEY_MEMORY_CONTEXT, "",
                WorkflowState.KEY_GENERATED_RESPONSE, generatedResponse,
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties(memoryEnabled)));
    }

    protected static WorkflowState state(ResponseContract responseContract) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "what's the weather",
                WorkflowState.KEY_SYSTEM_PROMPT, "system",
                WorkflowState.KEY_MEMORY_CONTEXT, "",
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties(responseContract)));
    }

    protected static AgentProperties agentProperties() {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                WorkflowId.STANDARD, ChatProviderId.Ollama, "agent-model", 0.9, "system", true,
                new WorkflowPolicy(List.of(), null, "fallback"));
    }

    protected static AgentProperties agentProperties(ResponseContract responseContract) {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                WorkflowId.STANDARD, ChatProviderId.Ollama, "agent-model", 0.5, "system", true,
                new WorkflowPolicy(List.of(), responseContract, "fallback"));
    }

    protected static AgentProperties agentProperties(boolean memoryEnabled) {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                WorkflowId.STANDARD, ChatProviderId.Ollama, "agent-model", 0.9, "system", memoryEnabled,
                new WorkflowPolicy(List.of(), null, "fallback"));
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
