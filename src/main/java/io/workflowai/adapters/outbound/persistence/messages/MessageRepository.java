package io.workflowai.adapters.outbound.persistence.messages;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

  List<MessageEntity> findByAgentIdAndConversationIdOrderByCreatedAtAsc(UUID agentId, UUID conversationId);
}