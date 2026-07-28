package io.workflowai.ports.inbound;

import io.workflowai.application.LlmProviderId;
import io.workflowai.domain.agents.AgentDefinition;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface AgentAdminManager {

    Map<LlmProviderId, Set<String>> supportedLlmProviders();

    List<AgentDefinition> getAllDefinitions();

    AgentDefinition getDefinition(UUID agentId);

    AgentDefinition saveDefinition(AgentDefinition definition);

    AgentDefinition updateDefinition(AgentDefinition definition);

    void deleteDefinition(UUID agentId);
}