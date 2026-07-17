package io.workflowai.ports.inbound;

import io.workflowai.domain.model.AgentDefinition;
import io.workflowai.domain.model.ProviderOption;

import java.util.List;
import java.util.UUID;

public interface AgentAdminPort {

    List<ProviderOption> supportedProviders();

    List<AgentDefinition> getAllDefinitions();

    AgentDefinition getDefinition(UUID agentId);

    AgentDefinition saveDefinition(AgentDefinition definition);

    AgentDefinition updateDefinition(AgentDefinition definition);

    void deleteDefinition(UUID agentId);
}