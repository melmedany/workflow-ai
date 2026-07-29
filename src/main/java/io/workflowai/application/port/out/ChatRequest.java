package io.workflowai.application.port.out;

public record ChatRequest(
        String model,
        double temperature,
        String systemPrompt,
        String message,
        String memoryContext) {
}
