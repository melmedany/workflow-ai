package io.workflowai.application.port.in;

import io.workflowai.domain.conversation.Conversation;
import io.workflowai.domain.conversation.ConversationMessage;

import java.util.List;
import java.util.UUID;

public interface ConversationProvider {
    Conversation createConversation(UUID agentId, String firstMessage);

    Conversation getConversation(UUID agentId, UUID id);

    List<Conversation> getConversationsForAgent(UUID agentId);

    List<ConversationMessage> getMessages(UUID agentId, UUID conversationId);

    void deleteConversation(UUID agentId, UUID id);
}
