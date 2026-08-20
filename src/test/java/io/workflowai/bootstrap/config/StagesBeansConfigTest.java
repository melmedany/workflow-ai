package io.workflowai.bootstrap.config;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.stage.StageSettings;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.bootstrap.StagesProperties;
import io.workflowai.bootstrap.StagesProperties.StageProperties;
import io.workflowai.domain.exceptions.ChatProviderCallException;
import io.workflowai.domain.workflow.StageId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StagesBeansConfigTest {

    private final ChatProvider provider = mock();
    private final StagesBeansConfig config = new StagesBeansConfig();

    @Test
    void validConfiguredModelsBuildTheStageSettingsBean() {
        when(provider.getId()).thenReturn(Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);
        ChatProviderRegistry registry = new ChatProviderRegistry(List.of(provider));
        StagesProperties properties = new StagesProperties(List.of(
                new StageProperties(StageId.CLASSIFICATION, Ollama, "llama3.2:3b", 0.2)));

        StageSettings settings = config.stageSettings(properties, registry);

        assertThat(settings.get(StageId.CLASSIFICATION).model()).isEqualTo("llama3.2:3b");
    }

    @Test
    void anUnsupportedConfiguredModelFailsAtStartupInsteadOfBuildingTheBean() {
        when(provider.getId()).thenReturn(Ollama);
        when(provider.supportsModel(anyString())).thenReturn(false);
        ChatProviderRegistry registry = new ChatProviderRegistry(List.of(provider));
        StagesProperties properties = new StagesProperties(List.of(
                new StageProperties(StageId.CLASSIFICATION, Ollama, "typo-d-model", 0.2)));

        assertThatThrownBy(() -> config.stageSettings(properties, registry))
                .isInstanceOf(ChatProviderCallException.class);
    }
}
