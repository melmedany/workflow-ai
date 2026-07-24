package io.workflowai.domain.model;

import io.workflowai.application.LLMProviderId;
import io.workflowai.domain.workflow.WorkflowPolicy;

import java.util.UUID;

/**
 * Flat domain model representing all need agent properties within the domain.
 */
public record AgentProperties(
        UUID id,
        String displayName,
        String description,
        boolean enabled,
        LLMProviderId llmProviderId,
        String model,
        double temperature,
        String systemPrompt,
        boolean memoryEnabled,
        WorkflowPolicy workflowPolicyProperties) {
}