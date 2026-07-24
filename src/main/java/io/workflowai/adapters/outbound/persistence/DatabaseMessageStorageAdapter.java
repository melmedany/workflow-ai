package io.workflowai.adapters.outbound.persistence;

import io.workflowai.adapters.outbound.persistence.messages.MessageEntity;
import io.workflowai.adapters.outbound.persistence.messages.MessageRepository;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.ports.outbound.MessageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DatabaseMessageStorageAdapter implements MessageStorage {

  private final MessageRepository repository;

  public DatabaseMessageStorageAdapter(MessageRepository repository) {
    this.repository = repository;
  }

  @Override
  @Transactional
  public void save(UUID conversationId, UUID agentId, ConversationMessage message) {
    repository.save(new MessageEntity(conversationId, agentId, message));
  }

  @Override
  public List<ConversationMessage> findByAgentIdAndConversationId(UUID agentId, UUID conversationId) {
    return repository.findByAgentIdAndConversationIdOrderByCreatedAtAsc(agentId, conversationId).stream()
        .map(e -> new ConversationMessage(e.role(), e.content(), e.addToMemory()))
        .toList();
  }
}