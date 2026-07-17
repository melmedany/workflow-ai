package io.workflowai.ports.inbound;

import io.workflowai.domain.model.Conversation;
import io.workflowai.domain.model.ConversationMessage;

import java.util.List;
import java.util.UUID;

public interface ConversationPort {
    Conversation createConversation(UUID agentId, String firstMessage);

    Conversation getConversation(UUID agentId, UUID id);

    List<Conversation> getConversationsForAgent(UUID agentId);

    List<ConversationMessage> getMessages(UUID agentId, UUID conversationId);

    void deleteConversation(UUID agentId, UUID id);
}
