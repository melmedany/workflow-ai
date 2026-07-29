package io.workflowai.domain.agent;

import io.workflowai.domain.workflow.WorkflowPolicy;

import java.io.Serializable;
import java.util.UUID;

/**
 * Flat domain model representing all need agent properties within the domain.
 */
public record AgentProperties(
        UUID id,
        String displayName,
        String description,
        boolean enabled,
        ChatProviderId chatProviderId,
        String model,
        double temperature,
        String systemPrompt,
        boolean memoryEnabled,
        WorkflowPolicy workflowPolicyProperties) implements Serializable {
}