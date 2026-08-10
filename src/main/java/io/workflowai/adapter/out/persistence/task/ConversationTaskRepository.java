package io.workflowai.adapter.out.persistence.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationTaskRepository extends JpaRepository<ConversationTaskEntity, UUID> {

    Optional<ConversationTaskEntity> findByIntentKey(String intentKey);

    List<ConversationTaskEntity> findByAgentIdAndConversationIdOrderByCreatedAtDesc(UUID agentId, UUID conversationId);
}