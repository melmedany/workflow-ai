package io.workflowai.ports.outbound;

import io.workflowai.domain.model.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationStorage {

    Conversation create(UUID agentId, String title);

    Optional<Conversation> findByAgentAndId(UUID agentId, UUID id);

    List<Conversation> findByAgent(UUID agentId);

    void delete(UUID agentId, UUID conversationId);
}