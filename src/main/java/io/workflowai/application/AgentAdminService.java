package io.workflowai.application;

import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.agents.AgentDefinition;
import io.workflowai.ports.inbound.AgentAdminManager;
import io.workflowai.ports.outbound.AgentDefinitionStorage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class AgentAdminService implements AgentAdminManager {
    private final AgentDefinitionStorage storagePort;
    private final LLMProviderRegistry llmProviderRegistry;

    public AgentAdminService(AgentDefinitionStorage storagePort, LLMProviderRegistry llmProviderRegistry) {
        this.storagePort = storagePort;
        this.llmProviderRegistry = llmProviderRegistry;
    }

    @Override
    public Map<LLMProviderId, Set<String>> supportedLLMProviders() {
        return llmProviderRegistry.supportedLLMProvider();
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
        return storagePort.save(definition);
    }

    @Override
    public AgentDefinition updateDefinition(AgentDefinition definition) {
        return storagePort.update(definition);
    }

    @Override
    public void deleteDefinition(UUID agentId) {
        storagePort.delete(agentId);
    }
}