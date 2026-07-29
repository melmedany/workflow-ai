package io.workflowai.domain.exceptions;

import io.workflowai.domain.workflow.StageId;

import java.util.UUID;

public class WorkflowStageException extends WorkflowExecutionException {

    public WorkflowStageException(UUID agentId, StageId stageId, String message) {
        this(agentId, stageId, message, null);
    }

    public WorkflowStageException(UUID agentId, StageId stageId, String message, Throwable cause) {
        super(agentId, "Stage [%s] failed: %s".formatted(stageId, message), cause);
    }
}
