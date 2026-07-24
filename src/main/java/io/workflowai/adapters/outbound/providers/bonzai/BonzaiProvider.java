package io.workflowai.adapters.outbound.providers.bonzai;

import io.workflowai.adapters.outbound.providers.AbstractOpenAiProvider;
import io.workflowai.application.LLMProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class BonzaiProvider extends AbstractOpenAiProvider {

    private static final Logger log = LoggerFactory.getLogger(BonzaiProvider.class);

    // TODO make configurable
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "claude-haiku-4-5", "claude-sonnet-4-6", "claude-sonnet-5",
            "claude-opus-4-8", "Qwen3.6-27B", "gemini-3.5-flash",
            "gemini-3.1-flash-lite", "glm-5", "glm-4.7",
            "glm-4.7-flash", "gpt-5.1", "gpt-5.4",
            "gpt-5.5", "gpt-5", "gpt-5-mini",
            "gpt-5-nano", "gpt-4.1", "gpt-4.1-mini",
            "gpt-4.1-nano", "o1", "o3", "o3-mini", "o4-mini");

    public BonzaiProvider(BonzaiProperties properties) {
        super(properties.baseUrl(), properties.apiKey(), properties.defaultModel(), properties.defaultTemperature());
        if (!properties.isConfigured()) {
            log.warn("[{}] api is not fully configured", getId());
        }
    }

    @Override
    public LLMProviderId getId() {
        return LLMProviderId.Bonzai;
    }

    @Override
    public boolean supportsModel(String model) {
        return model != null && SUPPORTED_MODELS.stream().anyMatch(model::equals);
    }

    @Override
    public Set<String> supportedModels() {
        return SUPPORTED_MODELS;
    }

    @ConfigurationProperties(prefix = "workflow-ai.providers.bonzai")
    public record BonzaiProperties(String baseUrl, String apiKey, String defaultModel, double defaultTemperature) {

        public boolean isConfigured() {
            return isConfigured(baseUrl) && isConfigured(apiKey);
        }

        private static boolean isConfigured(String value) {
            return value != null && !"not-configured".equals(value) && !value.isBlank();
        }
    }
}
