package io.workflowai.domain.agents;

import io.workflowai.domain.workflow.WorkflowPolicy;

import java.util.UUID;

public record AgentDefinition(
        UUID agentId,
        AgentDetails details,
        LlmProperties llmProperties,
        WorkflowPolicy workflowPolicyProperties) {
}