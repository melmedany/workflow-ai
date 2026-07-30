package io.workflowai.application.port.in;

import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.ChatProviderId;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface AgentAdminUseCase {

    Map<ChatProviderId, Set<String>> supportedChatProviders();

    List<AgentDefinition> getAllDefinitions();

    AgentDefinition getDefinition(UUID agentId);

    AgentDefinition saveDefinition(AgentDefinition definition);

    AgentDefinition updateDefinition(AgentDefinition definition);

    void deleteDefinition(UUID agentId);
}