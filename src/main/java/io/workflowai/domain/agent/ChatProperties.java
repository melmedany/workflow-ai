package io.workflowai.domain.agent;

public record ChatProperties(
        ChatProviderId providerId,
        String model,
        String agentPrompt,
        double temperature,
        boolean memoryEnabled) {
}