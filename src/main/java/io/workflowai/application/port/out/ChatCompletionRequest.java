package io.workflowai.application.port.out;

public record ChatCompletionRequest(
        String model,
        double temperature,
        String systemPrompt,
        String message,
        String memoryContext) {
}
