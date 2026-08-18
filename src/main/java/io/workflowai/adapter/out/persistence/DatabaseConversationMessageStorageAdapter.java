package io.workflowai.adapter.out.persistence;

import io.workflowai.adapter.out.persistence.conversation.message.MessageEntity;
import io.workflowai.adapter.out.persistence.conversation.message.MessageRepository;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.domain.conversation.ConversationMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DatabaseConversationMessageStorageAdapter implements ConversationMessageStorage {

  private final MessageRepository repository;

  public DatabaseConversationMessageStorageAdapter(MessageRepository repository) {
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
        .map(e -> new ConversationMessage(e.role(), e.content()))
        .toList();
  }
}