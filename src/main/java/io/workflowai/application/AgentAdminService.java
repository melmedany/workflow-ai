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
    private final LlmProviderRegistry llmProviderRegistry;

    public AgentAdminService(AgentDefinitionStorage storagePort, LlmProviderRegistry llmProviderRegistry) {
        this.storagePort = storagePort;
        this.llmProviderRegistry = llmProviderRegistry;
    }

    @Override
    public Map<LlmProviderId, Set<String>> supportedLlmProviders() {
        return llmProviderRegistry.supportedLlmProvider();
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
        llmProviderRegistry.validate(definition.llmProperties().providerId(), definition.llmProperties().model());
    }

    @Override
    public void deleteDefinition(UUID agentId) {
        storagePort.delete(agentId);
    }
}