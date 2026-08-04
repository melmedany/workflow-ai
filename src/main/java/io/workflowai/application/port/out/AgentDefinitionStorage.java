package io.workflowai.application.port.out;

import io.workflowai.domain.agent.AgentDefinition;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentDefinitionStorage {
    List<AgentDefinition> findAll();

    List<AgentDefinition> findEnabledAgents();

    Optional<AgentDefinition> findById(UUID agentId);

    AgentDefinition save(AgentDefinition definition);

    AgentDefinition update(AgentDefinition definition);

    void delete(UUID agentId);
}