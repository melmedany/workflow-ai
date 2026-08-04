package io.workflowai.adapter.in.rest.dto;

import io.workflowai.application.execution.Agent;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.conversation.Conversation;
import io.workflowai.domain.conversation.ConversationMessage;

public class Mappers {
    public static AgentInfo toAgentInfo(Agent agent) {
        return new AgentInfo(
                agent.properties().id(),
                agent.properties().displayName(),
                agent.properties().description(),
                agent.tags(),
                agent.properties().chatProviderId().name(),
                agent.properties().model()
        );
    }

    public static ConversationResponse toConversationResponse(Conversation c) {
        return new ConversationResponse(c.id(), c.agentId(), c.title(), c.createdAt(), c.updatedAt());
    }

    public static MessageResponse toMessageResponse(ConversationMessage cm) {
        return new MessageResponse(cm.role(), cm.content());
    }

    public static AgentSummaryDto toAgentSummary(AgentDefinition definition) {
        return new AgentSummaryDto(
                definition.agentId(),
                definition.details().displayName(),
                definition.details().enabled(),
                definition.chatProperties().providerId(),
                definition.chatProperties().model());
    }
}
