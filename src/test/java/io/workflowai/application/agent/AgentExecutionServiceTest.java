package io.workflowai.application.agent;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.port.out.ChatCompletionRequest;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentExecutionServiceTest {

    @Test
    void rejectsUnsupportedWorkflowAtSaveTime() {
        AgentExecutionService service = new AgentExecutionService(
                new NoopAgentDefinitionStorage(),
                new ChatProviderRegistry(List.of(new StubChatProvider())),
                new UnsupportingWorkflowExecutorFactory());

        assertThatThrownBy(() -> service.saveDefinition(agentDefinition()))
                .isInstanceOf(AgentValidationException.class)
                .hasMessageContaining("Unsupported workflow: %s".formatted(WorkflowId.STANDARD));
    }

    @Test
    void collectsProviderAndWorkflowErrorsIntoOneMessage() {
        AgentExecutionService service = new AgentExecutionService(
                new NoopAgentDefinitionStorage(),
                new ChatProviderRegistry(List.of()),
                new UnsupportingWorkflowExecutorFactory());

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

    private static final class UnsupportingWorkflowExecutorFactory extends WorkflowExecutorFactory {
        private UnsupportingWorkflowExecutorFactory() {
            super(List.of());
        }

        @Override
        public boolean isSupported(WorkflowId workflowId) {
            return false;
        }
    }

    private static final class NoopAgentDefinitionStorage implements AgentDefinitionStorage {
        @Override
        public List<AgentDefinition> findAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AgentDefinition> findEnabledAgents() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AgentDefinition> findById(UUID agentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentDefinition save(AgentDefinition definition) {
            throw new AssertionError("save() must not be reached when validation fails");
        }

        @Override
        public AgentDefinition update(AgentDefinition definition) {
            throw new AssertionError("update() must not be reached when validation fails");
        }

        @Override
        public void delete(UUID agentId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubChatProvider implements ChatProvider {
        @Override
        public ChatProviderId getId() {
            return ChatProviderId.Ollama;
        }

        @Override
        public String stream(ChatCompletionRequest request, Consumer<String> tokenConsumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String call(ChatCompletionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean supportsModel(String model) {
            return true;
        }

        @Override
        public Set<String> supportedModels() {
            return Set.of();
        }
    }
}
