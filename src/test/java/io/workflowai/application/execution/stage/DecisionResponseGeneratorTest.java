package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.workflowai.application.execution.stage.StageSettings.StageSetting;
import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DecisionResponseGeneratorTest {

    private final ChatProvider provider = mock();

    @Test
    void returnsProviderResponseOnSuccess() {
        when(provider.getId()).thenReturn(Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);
        when(provider.stream(any(ChatCompletionRequest.class), any())).thenReturn("Hello there!");

        String result = generator().generate(StagesUtil.state(StagesUtil.decision(StageId.GENERATE_GREETING)),
                StageId.GENERATE_GREETING, "prompt");

        assertThat(result).isEqualTo("Hello there!");
    }

    @Test
    void returnsPolicyFallbackMessageWhenProviderCallFails() {
        when(provider.getId()).thenReturn(Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);
        when(provider.stream(any(ChatCompletionRequest.class), any())).thenThrow(new RuntimeException("boom"));

        WorkflowState state = StagesUtil.state(StagesUtil.decision(StageId.GENERATE_GREETING));

        String result = generator().generate(state, StageId.GENERATE_GREETING, "prompt");

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void returnsPolicyFallbackMessageWhenProviderIsUnknown() {
        ChatProviderRegistry emptyRegistry = new ChatProviderRegistry(List.of());
        DecisionResponseGenerator generator = new DecisionResponseGenerator(emptyRegistry, settings());

        WorkflowState state = StagesUtil.state(StagesUtil.decision(StageId.GENERATE_GREETING));

        String result = generator.generate(state, StageId.GENERATE_GREETING, "prompt");

        assertThat(result).isEqualTo("fallback");
    }

    private DecisionResponseGenerator generator() {
        return new DecisionResponseGenerator(new ChatProviderRegistry(List.of(provider)), settings());
    }

    private StageSettings settings() {
        return new StageSettings(List.of(
                new StageSetting(StageId.GENERATE_GREETING, Ollama, "greeting-model", 0.5)));
    }
}
