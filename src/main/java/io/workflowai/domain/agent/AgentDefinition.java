package io.workflowai.domain.agent;

import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.workflow.WorkflowPolicy;

import java.util.UUID;

public record AgentDefinition(
        UUID agentId,
        AgentDetails details,
        WorkflowId workflowId,
        ChatProperties chatProperties,
        WorkflowPolicy workflowPolicy) {
}