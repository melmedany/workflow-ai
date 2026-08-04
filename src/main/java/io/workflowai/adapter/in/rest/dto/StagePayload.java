package io.workflowai.adapter.in.rest.dto;

import io.workflowai.domain.workflow.StageId;

public record StagePayload(StageId stageId, StageStatus status, String label, String reason) {

    public enum StageStatus {
        STARTED, COMPLETED, FAILED
    }
}
