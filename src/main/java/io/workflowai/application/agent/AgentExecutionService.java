package io.workflowai.application.agent;

import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.exceptions.AgentValidationException;
import io.workflowai.domain.exceptions.ChatProviderException;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.workflow.WorkflowExecutorFactory;
import io.workflowai.application.port.in.AgentAdminUseCase;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.execution.ChatProviderRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AgentExecutionService implements AgentAdminUseCase {
    private final AgentDefinitionStorage storagePort;
    private final ChatProviderRegistry chatProviderRegistry;
    private final WorkflowExecutorFactory workflowExecutorFactory;

    public AgentExecutionService(AgentDefinitionStorage storagePort, ChatProviderRegistry chatProviderRegistry,
                                  WorkflowExecutorFactory workflowExecutorFactory) {
        this.storagePort = storagePort;
        this.chatProviderRegistry = chatProviderRegistry;
        this.workflowExecutorFactory = workflowExecutorFactory;
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
        List<String> errors = new ArrayList<>();
        try {
            chatProviderRegistry.validate(definition.chatProperties().providerId(), definition.chatProperties().model());
        } catch (IllegalArgumentException | ChatProviderException ex) {
            errors.add(ex.getMessage());
        }
        if (!workflowExecutorFactory.isSupported(definition.workflowId())) {
            errors.add("Unsupported workflow: %s".formatted(definition.workflowId()));
        }
        if (!errors.isEmpty()) {
            throw new AgentValidationException(String.join("; ", errors));
        }
    }

    @Override
    public void deleteDefinition(UUID agentId) {
        storagePort.delete(agentId);
    }
}