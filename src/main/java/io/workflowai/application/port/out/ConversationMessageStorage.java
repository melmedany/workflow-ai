package io.workflowai.application.port.out;

import io.workflowai.domain.conversation.ConversationMessage;

import java.util.List;
import java.util.UUID;

public interface ConversationMessageStorage {

    void save(UUID conversationId, UUID agentId, ConversationMessage message);

    List<ConversationMessage> findByAgentIdAndConversationId(UUID agentId, UUID conversationId);
}
