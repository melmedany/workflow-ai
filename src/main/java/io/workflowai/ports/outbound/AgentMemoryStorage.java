package io.workflowai.ports.outbound;

import java.util.Optional;
import java.util.UUID;

public interface AgentMemoryStorage {

    Optional<String> getMemory(UUID conversationId, UUID agentId);

    void replace(UUID conversationId, UUID agentId, String content);

    void clear(UUID conversationId);
}
