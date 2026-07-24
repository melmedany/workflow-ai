package io.workflowai.adapters.inbound.rest.dto;

import io.workflowai.domain.workflow.StageId;

import java.io.Serializable;

public record StagePayload(StageId stageId, StageStatus status, String label, String reason) implements Serializable {

    public enum StageStatus {
        STARTED, COMPLETED, FAILED
    }
}
