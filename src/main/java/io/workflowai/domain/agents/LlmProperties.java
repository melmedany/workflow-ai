package io.workflowai.domain.agents;

import io.workflowai.application.LLMProviderId;

public record LlmProperties(
        LLMProviderId providerId,
        String model,
        String agentPrompt,
        double temperature,
        boolean memoryEnabled) {
}