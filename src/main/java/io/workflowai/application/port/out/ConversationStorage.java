package io.workflowai.application.port.out;

import io.workflowai.domain.conversation.Conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationStorage {

    Conversation create(UUID agentId, String title);

    Optional<Conversation> findByAgentAndId(UUID agentId, UUID id);

    List<Conversation> findByAgent(UUID agentId);

    void delete(UUID agentId, UUID conversationId);
}