package io.workflowai.domain.workflow;

import static io.workflowai.domain.workflow.WorkflowOutcome.COMPLETED;
import static io.workflowai.domain.workflow.WorkflowOutcome.FAILED;
import static io.workflowai.domain.workflow.WorkflowOutcome.TIMED_OUT;

public record WorkflowExecutionResult(WorkflowOutcome outcome, String message, Throwable cause) {

    public static WorkflowExecutionResult completed() {
        return new WorkflowExecutionResult(COMPLETED, null, null);
    }

    public static WorkflowExecutionResult failed(String message, Throwable cause) {
        return new WorkflowExecutionResult(FAILED, message, cause);
    }

    public static WorkflowExecutionResult timedOut(String message) {
        return new WorkflowExecutionResult(TIMED_OUT, message, null);
    }
}