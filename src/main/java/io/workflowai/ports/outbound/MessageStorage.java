package io.workflowai.ports.outbound;

import io.workflowai.domain.model.ConversationMessage;

import java.util.List;
import java.util.UUID;

public interface MessageStorage {

    void save(UUID conversationId, UUID agentId, ConversationMessage message);

    List<ConversationMessage> findByAgentIdAndConversationId(UUID agentId, UUID conversationId);
}
