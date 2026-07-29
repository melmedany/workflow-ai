package io.workflowai.adapter.out.persistence;

import io.workflowai.adapter.out.persistence.agent.memory.AgentMemoryEntity;
import io.workflowai.adapter.out.persistence.agent.memory.AgentMemoryRepository;
import io.workflowai.application.port.out.AgentMemoryStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DatabaseAgentMemoryStorageAdapter implements AgentMemoryStorage {

  private final AgentMemoryRepository repository;

  public DatabaseAgentMemoryStorageAdapter(AgentMemoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<String> getMemory(UUID conversationId, UUID agentId) {
    return repository
        .findByConversationIdAndAgentId(conversationId, agentId)
        .map(AgentMemoryEntity::content);
  }

  @Override
  @Transactional
  public void replace(UUID conversationId, UUID agentId, String content) {
    AgentMemoryEntity memory = repository
        .findByConversationIdAndAgentId(conversationId, agentId)
        .orElseGet(() -> new AgentMemoryEntity(conversationId, agentId, content));
    memory.replaceContent(content);
    repository.save(memory);
  }

  @Override
  @Transactional
  public void clear(UUID conversationId) {
    repository.deleteByConversationId(conversationId);
  }
}