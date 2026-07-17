package io.workflowai.adapters.outbound.providers.ollama;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "langchain4j.providers.ollama")
public record OllamaProperties(String baseUrl, String defaultModel, double temperature) {

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && !baseUrl.equals("not-configured");
    }
}
