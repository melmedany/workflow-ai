package io.workflowai.application.agent;

import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.application.port.in.AgentAdminManager;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.execution.ChatProviderRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AgentAdminService implements AgentAdminManager {
    private final AgentDefinitionStorage storagePort;
    private final ChatProviderRegistry chatProviderRegistry;

    public AgentAdminService(AgentDefinitionStorage storagePort, ChatProviderRegistry chatProviderRegistry) {
        this.storagePort = storagePort;
        this.chatProviderRegistry = chatProviderRegistry;
    }

    @Override
    public Map<ChatProviderId, Set<String>> supportedChatProviders() {
        return chatProviderRegistry.supportedChatProviders();
    }

    @Override
    public List<AgentDefinition> getAllDefinitions() {
        return storagePort.findAll();
    }

    @Override
    public AgentDefinition getDefinition(UUID agentId) {
        return storagePort.findById(agentId).orElseThrow(() -> new AgentNotFoundException(agentId));
    }

    @Override
    public AgentDefinition saveDefinition(AgentDefinition definition) {
        validate(definition);
        return storagePort.save(definition);
    }

    @Override
    public AgentDefinition updateDefinition(AgentDefinition definition) {
        validate(definition);
        return storagePort.update(definition);
    }

    private void validate(AgentDefinition definition) {
        chatProviderRegistry.validate(definition.chatProperties().providerId(), definition.chatProperties().model());
    }

    @Override
    public void deleteDefinition(UUID agentId) {
        storagePort.delete(agentId);
    }
}