package io.workflowai.domain.exceptions;

public class WorkflowBuildException extends DomainException {

    public WorkflowBuildException(String message) {
        this(message, null);
    }

    public WorkflowBuildException(String message, Throwable cause) {
        super("Workflow build failed: %s".formatted(message), cause);
    }
}
