package io.workflowai.domain.agent;

import io.workflowai.domain.workflow.WorkflowPolicy;

import java.util.UUID;

public record AgentDefinition(
        UUID agentId,
        AgentDetails details,
        ChatProperties chatProperties,
        WorkflowPolicy workflowPolicyProperties) {
}