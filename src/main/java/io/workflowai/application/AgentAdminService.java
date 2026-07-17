package io.workflowai.application;

import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.model.AgentDefinition;
import io.workflowai.domain.model.ProviderOption;
import io.workflowai.ports.inbound.AgentAdminPort;
import io.workflowai.ports.outbound.AgentDefinitionStoragePort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AgentAdminService implements AgentAdminPort {
    private final AgentDefinitionStoragePort storagePort;
    private final ProviderRegistry providerRegistry;

    public AgentAdminService(AgentDefinitionStoragePort storagePort, ProviderRegistry providerRegistry) {
        this.storagePort = storagePort;
        this.providerRegistry = providerRegistry;
    }

    @Override
    public List<ProviderOption> supportedProviders() {
        return providerRegistry.supportedOptions();
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