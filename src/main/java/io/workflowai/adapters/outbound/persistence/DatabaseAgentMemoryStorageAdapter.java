package io.workflowai.adapters.outbound.persistence;

import io.workflowai.adapters.outbound.persistence.agentmemory.AgentMemoryEntity;
import io.workflowai.adapters.outbound.persistence.agentmemory.AgentMemoryRepository;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.domain.model.ConversationMessageRole;
import io.workflowai.ports.outbound.AgentMemoryStoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DatabaseAgentMemoryStorageAdapter implements AgentMemoryStoragePort {

  private final AgentMemoryRepository repository;

  public DatabaseAgentMemoryStorageAdapter(AgentMemoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<ConversationMessage> getHistory(UUID conversationId, UUID agentId) {
    return repository
        .findByConversationIdAndAgentIdOrderByCreatedAtAsc(conversationId, agentId)
        .stream()
        .map(e -> new ConversationMessage(ConversationMessageRole.AGENT, e.content()))
        .toList();
  }

  @Override
  @Transactional
  public void add(UUID conversationId, UUID agentId, String content) {
    repository.save(new AgentMemoryEntity(conversationId, agentId, content));
  }

  @Override
  @Transactional
  public void clear(UUID conversationId) {
    repository.deleteByConversationId(conversationId);
  }
}