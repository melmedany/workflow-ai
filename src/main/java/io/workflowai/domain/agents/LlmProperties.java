package io.workflowai.domain.agents;

import io.workflowai.application.LlmProviderId;

public record LlmProperties(
        LlmProviderId providerId,
        String model,
        String agentPrompt,
        double temperature,
        boolean memoryEnabled) {
}