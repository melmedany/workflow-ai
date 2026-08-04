package io.workflowai.adapter.out.persistence;

import io.workflowai.adapter.out.persistence.agent.AgentEntity;
import io.workflowai.adapter.out.persistence.agent.AgentRepository;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.exceptions.AgentNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DatabaseAgentDefinitionStorageAdapter implements AgentDefinitionStorage {

    private final AgentRepository repository;

    public DatabaseAgentDefinitionStorageAdapter(AgentRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AgentDefinition> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public List<AgentDefinition> findEnabledAgents() {
        return repository.findEnabledAgents().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AgentDefinition> findById(UUID agentId) {
        return repository.findById(agentId).map(this::toDomain);
    }

    @Override
    @Transactional
    public AgentDefinition save(AgentDefinition definition) {
        AgentEntity entity = new AgentEntity(definition.details(), definition.workflowId(),
                definition.chatProperties(), definition.workflowPolicy());
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public AgentDefinition update(AgentDefinition definition) {
        AgentEntity entity = repository.findById(definition.agentId())
                .orElseThrow(() -> new AgentNotFoundException(definition.agentId()));
        entity.update(definition.details(), definition.workflowId(),
                definition.chatProperties(), definition.workflowPolicy());
        return toDomain(repository.save(entity));
    }

    @Override
    public void delete(UUID agentId) {
        repository.deleteById(agentId);
    }

    private AgentDefinition toDomain(AgentEntity entity) {
        return new AgentDefinition(
                entity.id(),
                entity.details(),
                entity.workflowId(),
                entity.chatProperties(),
                entity.workflowPolicy());
    }
}