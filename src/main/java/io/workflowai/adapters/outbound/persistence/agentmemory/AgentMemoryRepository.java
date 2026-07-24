package io.workflowai.adapters.outbound.persistence.agentmemory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentMemoryRepository extends JpaRepository<AgentMemoryEntity, UUID> {

    Optional<AgentMemoryEntity> findByConversationIdAndAgentId(UUID conversationId, UUID agentId);

    @Modifying
    @Transactional
    void deleteByConversationId(UUID conversationId);
}