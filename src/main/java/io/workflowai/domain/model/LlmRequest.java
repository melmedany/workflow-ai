package io.workflowai.domain.model;

public record LlmRequest(
        String model,
        double temperature,
        String systemPrompt,
        String message,
        String memoryContext) {
}
