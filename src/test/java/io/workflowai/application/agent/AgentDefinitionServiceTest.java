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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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