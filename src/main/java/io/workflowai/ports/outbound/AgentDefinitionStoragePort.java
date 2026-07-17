package io.workflowai.ports.outbound;

import io.workflowai.domain.model.AgentDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentDefinitionStoragePort {
    List<AgentDefinition> findAll();

    Optional<AgentDefinition> findById(UUID agentId);

    AgentDefinition save(AgentDefinition definition);

    AgentDefinition update(AgentDefinition definition);

    void delete(UUID agentId);
}