package io.workflowai.adapters.inbound.rest.dto;

import io.workflowai.domain.agents.Agent;
import io.workflowai.domain.model.Conversation;
import io.workflowai.domain.model.ConversationMessage;

public class Mappers {
    public static AgentInfo toAgentInfo(Agent agent) {
        return new AgentInfo(
                agent.properties().id(),
                agent.properties().displayName(),
                agent.properties().description(),
                agent.tags(),
                agent.properties().llmProviderId().name(),
                agent.properties().model()
        );
    }

    public static ConversationResponse toConversationResponse(Conversation c) {
        return new ConversationResponse(c.id(), c.agentId(), c.title(), c.createdAt(), c.updatedAt());
    }

    public static MessageResponse toMessageResponse(ConversationMessage cm) {
        return new MessageResponse(cm.role(), cm.content());
    }
}
