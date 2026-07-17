package io.workflowai.adapters.outbound.providers.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "langchain4j.providers.openai")
public record OpenAiProperties(String baseUrl, String apiKey, String defaultModel, double temperature) {

    public boolean isConfigured() {
        return isConfigured(baseUrl) && isConfigured(apiKey);
    }

    private static boolean isConfigured(String value) {
        return value != null && !"not-configured".equals(value) && !value.isBlank();
    }
}
