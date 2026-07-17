package io.workflowai.adapters.outbound.persistence;

import io.workflowai.adapters.outbound.persistence.agents.AgentEntity;
import io.workflowai.adapters.outbound.persistence.agents.AgentRepository;
import io.workflowai.domain.model.AgentDefinition;
import io.workflowai.domain.model.AgentDetails;
import io.workflowai.domain.model.LlmConfig;
import io.workflowai.domain.model.PolicyConfig;
import io.workflowai.ports.outbound.AgentDefinitionStoragePort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class DatabaseAgentDefinitionStorageAdapter implements AgentDefinitionStoragePort {

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
        AgentEntity entity = new AgentEntity(write(definition.details()), write(definition.llmConfig()), write(definition.policyConfig()));
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public AgentDefinition update(AgentDefinition definition) {
        AgentEntity entity = repository.findById(definition.agentId())
                .orElseGet(() -> new AgentEntity("{}", "{}", "{}"));
        // TODO fail to update when agentId is not found
        entity.update(write(definition.details()), write(definition.llmConfig()), write(definition.policyConfig()));
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
                read(entity.llmConfig(), LlmConfig.class),
                read(entity.policyConfig(), PolicyConfig.class));
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid agent JSON configuration", e);
        }
    }

    private String write(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize agent configuration", e);
        }
    }
}