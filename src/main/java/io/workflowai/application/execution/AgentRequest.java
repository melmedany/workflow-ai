package io.workflowai.application.execution;

import io.workflowai.domain.agent.TriggerSource;

import java.util.UUID;

public record AgentRequest(TriggerSource triggerSource, UUID agentId, UUID conversationId, String message) {

    public static AgentRequest userMessage(UUID agentId, UUID conversationId, String message) {
        return new AgentRequest(TriggerSource.USER_MESSAGE, agentId, conversationId, message);
    }

    public static AgentRequest systemTrigger(UUID agentId, UUID conversationId, String message) {
        return new AgentRequest(TriggerSource.SYSTEM_TRIGGER, agentId, conversationId, message);
    }
}
