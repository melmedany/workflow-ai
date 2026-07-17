package io.workflowai.domain.model;

public record LlmConfig(
        String provider,
        String model,
        String agentPrompt,
        double temperature,
        boolean memoryEnabled,
        boolean validationEnabled,
        int memoryLimit) {
}