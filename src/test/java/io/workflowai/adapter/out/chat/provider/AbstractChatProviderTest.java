package io.workflowai.adapter.out.chat.provider;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.exceptions.ChatProviderCallException;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AbstractChatProviderTest {

    private final FakeChatProvider provider = new FakeChatProvider(Set.of("supported-model"));

    @Test
    void supportedModelIsReturnedUnchanged() {
        assertThat(provider.resolveModel("supported-model")).isEqualTo("supported-model");
    }

    @Test
    void unsupportedModelThrowsInsteadOfSilentlyFallingBackToADefault() {
        assertThatThrownBy(() -> provider.resolveModel("typo-d-model"))
                .isInstanceOf(ChatProviderCallException.class)
                .hasMessageContaining("typo-d-model");
    }

    private static class FakeChatProvider extends AbstractChatProvider {

        private final Set<String> supported;

        FakeChatProvider(Set<String> supported) {
            super(mock(InputGuardrail.class), mock(OutputGuardrail.class));
            this.supported = supported;
        }

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
            return supported.contains(model);
        }

        @Override
        public Set<String> supportedModels() {
            return supported;
        }

        @Override
        protected ChatModel buildChatModel(String model, double temperature) {
            throw new UnsupportedOperationException();
        }

        @Override
        protected StreamingChatModel buildStreamingModel(String model, double temperature) {
            throw new UnsupportedOperationException();
        }
    }
}
