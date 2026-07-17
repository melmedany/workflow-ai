package io.workflowai.adapters.outbound.providers.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "langchain4j.providers.anthropic")
public record AnthropicProperties(String apiKey, String defaultModel, double temperature) {

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("not-configured");
    }
}
