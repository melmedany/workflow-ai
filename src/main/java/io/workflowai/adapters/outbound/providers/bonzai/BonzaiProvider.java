package io.workflowai.adapters.outbound.providers.bonzai;

import io.workflowai.adapters.outbound.providers.AbstractOpenAiProvider;
import io.workflowai.application.LlmProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class BonzaiProvider extends AbstractOpenAiProvider {

    private static final Logger log = LoggerFactory.getLogger(BonzaiProvider.class);

    private final BonzaiProperties properties;

    public BonzaiProvider(BonzaiProperties properties) {
        super(properties.baseUrl(), properties.apiKey(), properties.defaultModel(), properties.defaultTemperature());
        this.properties = properties;
        if (!properties.isConfigured()) {
            log.warn("[{}] api is not fully configured", getId());
        }
    }

    @Override
    public LlmProviderId getId() {
        return LlmProviderId.Bonzai;
    }

    @Override
    public boolean supportsModel(String model) {
        return model != null && properties.supportedModels().contains(model);
    }

    @Override
    public Set<String> supportedModels() {
        return properties.supportedModels();
    }

    @ConfigurationProperties(prefix = "workflow-ai.providers.bonzai")
    public record BonzaiProperties(String baseUrl, String apiKey, String defaultModel,
                                   double defaultTemperature, Set<String> supportedModels) {

        public boolean isConfigured() {
            return isConfigured(baseUrl) && isConfigured(apiKey);
        }

        private static boolean isConfigured(String value) {
            return value != null && !"not-configured".equals(value) && !value.isBlank();
        }
    }
}
