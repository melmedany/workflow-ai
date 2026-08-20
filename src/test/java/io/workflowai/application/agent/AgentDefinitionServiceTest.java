package io.workflowai.application.agent;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.AgentDetails;
import io.workflowai.domain.agent.ChatProperties;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.exceptions.AgentValidationException;
import io.workflowai.domain.workflow.WorkflowExecutorFactory;
import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentDefinitionServiceTest {

    private final AgentDefinitionStorage storage = mock();
    private final ChatProvider chatProvider = mock();
    private final WorkflowExecutorFactory workflowExecutorFactory = mock();
    private final AgentUseCase agentService = mock();

    @Test
    void rejectsUnsupportedWorkflowAtSaveTime() {
        when(chatProvider.getId()).thenReturn(ChatProviderId.Ollama);
        when(workflowExecutorFactory.isSupported(WorkflowId.STANDARD)).thenReturn(false);

        AgentDefinitionService service = new AgentDefinitionService(
                storage,
                new ChatProviderRegistry(List.of(chatProvider)),
                workflowExecutorFactory,
                agentService);

        assertThatThrownBy(() -> service.saveDefinition(agentDefinition()))
                .isInstanceOf(AgentValidationException.class)
                .hasMessageContaining("Unsupported workflow: %s".formatted(WorkflowId.STANDARD));
    }

    @Test
    void collectsProviderAndWorkflowErrorsIntoOneMessage() {
        AgentDefinitionService service = new AgentDefinitionService(
                storage,
                new ChatProviderRegistry(List.of()),
                workflowExecutorFactory,
                agentService);
        when(workflowExecutorFactory.isSupported(WorkflowId.STANDARD)).thenReturn(false);

        assertThatThrownBy(() -> service.saveDefinition(agentDefinition()))
                .isInstanceOf(AgentValidationException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .contains("Unknown provider")
                        .contains("Unsupported workflow"));

        verifyNoInteractions(storage);
        verifyNoInteractions(agentService);
    }

    @Test
    void rejectsUnsupportedModelForAKnownProvider() {
        when(chatProvider.getId()).thenReturn(ChatProviderId.Ollama);
        when(chatProvider.supportsModel("agent-model")).thenReturn(false);
        when(chatProvider.supportedModels()).thenReturn(Set.of("other-model"));
        when(workflowExecutorFactory.isSupported(WorkflowId.STANDARD)).thenReturn(true);

        AgentDefinitionService service = new AgentDefinitionService(
                storage,
                new ChatProviderRegistry(List.of(chatProvider)),
                workflowExecutorFactory,
                agentService);

        assertThatThrownBy(() -> service.saveDefinition(agentDefinition()))
                .isInstanceOf(AgentValidationException.class)
                .hasMessageContaining("Unsupported model");

        verifyNoInteractions(storage);
        verifyNoInteractions(agentService);
    }

    @Test
    void savesAndReloadsWhenValidationPasses() {
        when(chatProvider.getId()).thenReturn(ChatProviderId.Ollama);
        when(chatProvider.supportsModel("agent-model")).thenReturn(true);
        when(workflowExecutorFactory.isSupported(WorkflowId.STANDARD)).thenReturn(true);

        AgentDefinition definition = agentDefinition();
        when(storage.save(definition)).thenReturn(definition);

        AgentDefinitionService service = new AgentDefinitionService(
                storage,
                new ChatProviderRegistry(List.of(chatProvider)),
                workflowExecutorFactory,
                agentService);

        AgentDefinition result = service.saveDefinition(definition);

        assertThat(result).isEqualTo(definition);
        var order = inOrder(storage, agentService);
        order.verify(storage).save(definition);
        order.verify(agentService).reload(definition.agentId());
    }

    @Test
    void updateFollowsTheSameValidateThenPersistThenReloadContract() {
        when(chatProvider.getId()).thenReturn(ChatProviderId.Ollama);
        when(chatProvider.supportsModel("agent-model")).thenReturn(true);
        when(workflowExecutorFactory.isSupported(WorkflowId.STANDARD)).thenReturn(true);

        AgentDefinition definition = agentDefinition();
        AgentDefinition updated = agentDefinition();
        when(storage.update(definition)).thenReturn(updated);

        AgentDefinitionService service = new AgentDefinitionService(
                storage,
                new ChatProviderRegistry(List.of(chatProvider)),
                workflowExecutorFactory,
                agentService);

        AgentDefinition result = service.updateDefinition(definition);

        assertThat(result).isEqualTo(updated);
        verify(agentService).reload(updated.agentId());
    }

    @Test
    void deleteRemovesFromRuntimeBeforeDeletingFromStorage() {
        AgentDefinitionService service = new AgentDefinitionService(
                storage,
                new ChatProviderRegistry(List.of()),
                workflowExecutorFactory,
                agentService);
        UUID agentId = UUID.randomUUID();

        service.deleteDefinition(agentId);

        var order = inOrder(agentService, storage);
        order.verify(agentService).remove(agentId);
        order.verify(storage).delete(agentId);
    }

    private AgentDefinition agentDefinition() {
        return new AgentDefinition(
                UUID.randomUUID(),
                new AgentDetails("agent", "description", true),
                WorkflowId.STANDARD,
                new ChatProperties(ChatProviderId.Ollama, "agent-model", "system", 0.5, true),
                new WorkflowPolicy(List.of(), null, "fallback"));
    }
}