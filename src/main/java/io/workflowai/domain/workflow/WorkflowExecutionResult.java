package io.workflowai.domain.workflow;

public record WorkflowExecutionResult(WorkflowOutcome outcome, String message, Throwable cause) {

    public static WorkflowExecutionResult completed() {
        return new WorkflowExecutionResult(WorkflowOutcome.COMPLETED, null, null);
    }

    public static WorkflowExecutionResult failed(String message, Throwable cause) {
        return new WorkflowExecutionResult(WorkflowOutcome.FAILED, message, cause);
    }

    public static WorkflowExecutionResult timedOut(String message) {
        return new WorkflowExecutionResult(WorkflowOutcome.TIMED_OUT, message, null);
    }
}