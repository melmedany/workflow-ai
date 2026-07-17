package io.workflowai.adapters.outbound.providers.bonzai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workflow-ai.providers.bonzai")
public record BonzaiProperties(String baseUrl, String apiKey, String model, double temperature) {

    public boolean isConfigured() {
        return isConfigured(baseUrl) && isConfigured(apiKey);
    }

    private static boolean isConfigured(String value) {
        return value != null && !"not-configured".equals(value) && !value.isBlank();
    }
}
