package io.workflowai.domain.model;

public record LLMRequest(
        String model,
        double temperature,
        String systemPrompt,
        String message,
        String memoryContext) {
}
