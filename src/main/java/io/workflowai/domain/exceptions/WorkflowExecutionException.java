package io.workflowai.domain.exceptions;

import java.util.UUID;

public class WorkflowExecutionException extends DomainException {

    public WorkflowExecutionException(UUID agentId, String message) {
        super("Workflow execution failed for agent [%s]: %s".formatted(agentId, message));
    }

    public WorkflowExecutionException(UUID agentId, String message, Throwable cause) {
        super("Workflow execution failed for agent [%s]: %s".formatted(agentId, message), cause);
    }
}
