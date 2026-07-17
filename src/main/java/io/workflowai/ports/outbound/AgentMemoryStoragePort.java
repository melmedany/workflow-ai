package io.workflowai.ports.outbound;

import io.workflowai.domain.model.ConversationMessage;

import java.util.List;
import java.util.UUID;

public interface AgentMemoryStoragePort {

    List<ConversationMessage> getHistory(UUID conversationId, UUID agentId);

    void add(UUID conversationId, UUID agentId, String content);

    void clear(UUID conversationId);
}
