package io.workflowai.adapters.outbound.persistence.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    List<ConversationEntity> findByAgentIdOrderByCreatedAtDesc(UUID agentId);

    Optional<ConversationEntity> findByAgentIdAndId(UUID agentId, UUID id);

    void deleteByAgentIdAndId(UUID agentId, UUID id);
}