package io.workflowai.application.port.out;

import io.workflowai.domain.agent.AgentDefinition;

import java.util.List;
import java.util.UUID;

public interface AgentDefinitionStorage {
    List<AgentDefinition> findAll();

    AgentDefinition findById(UUID agentId);

    AgentDefinition save(AgentDefinition definition);

    AgentDefinition update(AgentDefinition definition);

    void delete(UUID agentId);
}