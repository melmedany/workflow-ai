package io.workflowai.domain.model;

import java.util.UUID;

public record AgentDefinition(
        UUID agentId,
        AgentDetails details,
        LlmConfig llmConfig,
        PolicyConfig policyConfig) {
}