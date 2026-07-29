package io.workflowai.adapter.out.persistence;

import io.workflowai.adapter.out.persistence.agent.AgentEntity;
import io.workflowai.adapter.out.persistence.agent.AgentRepository;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.AgentDetails;
import io.workflowai.domain.agent.ChatProperties;
import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DatabaseAgentDefinitionStorageAdapter implements AgentDefinitionStorage {

    private final AgentRepository repository;
    private final JsonMapper jsonMapper;

    public DatabaseAgentDefinitionStorageAdapter(AgentRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<AgentDefinition> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AgentDefinition> findById(UUID agentId) {
        return repository.findById(agentId).map(this::toDomain);
    }

    @Override
    @Transactional
    public AgentDefinition save(AgentDefinition definition) {
        AgentEntity entity = new AgentEntity(write(definition.details()), write(definition.chatProperties()), write(definition.workflowPolicyProperties()));
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public AgentDefinition update(AgentDefinition definition) {
        AgentEntity entity = repository.findById(definition.agentId())
                .orElseThrow(() -> new AgentNotFoundException(definition.agentId()));
        entity.update(write(definition.details()), write(definition.chatProperties()), write(definition.workflowPolicyProperties()));
        return toDomain(repository.save(entity));
    }

    @Override
    public void delete(UUID agentId) {
        repository.deleteById(agentId);
    }

    private AgentDefinition toDomain(AgentEntity entity) {
        return new AgentDefinition(
                entity.id(),
                read(entity.details(), AgentDetails.class),
                read(entity.chatProperties(), ChatProperties.class),
                read(entity.workflowPolicyProperties(), WorkflowPolicy.class));
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid agent JSON configuration", ex);
        }
    }

    private String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize agent configuration", ex);
        }
    }
}