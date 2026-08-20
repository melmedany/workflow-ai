package io.workflowai.adapter.out.persistence.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationTaskRepository extends JpaRepository<ConversationTaskEntity, UUID> {

    @Query(value = "SELECT * " +
            "FROM conversation_tasks " +
            "WHERE agent_id = :agentId " +
            "AND conversation_id = :conversationId " +
            "AND definition ->> 'intentKey' = :intentKey ", nativeQuery = true)
    Optional<ConversationTaskEntity> findTaskByIntent(UUID agentId, UUID conversationId, String intentKey);

    @Query(value = "SELECT * " +
            "FROM conversation_tasks " +
            "WHERE agent_id = :agentId " +
            "AND conversation_id = :conversationId " +
            "AND id = :taskId " +
            "AND schedule ->> 'status' = :status", nativeQuery = true)
    Optional<ConversationTaskEntity> findTaskWithStatus(UUID agentId, UUID conversationId, UUID taskId, String status);

    Optional<ConversationTaskEntity> findByAgentIdAndConversationIdAndId(UUID agentId, UUID conversationId, UUID id);

    List<ConversationTaskEntity> findByAgentIdAndConversationIdOrderByCreatedAtDesc(UUID agentId, UUID conversationId);
}