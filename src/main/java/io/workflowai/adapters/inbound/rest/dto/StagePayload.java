package io.workflowai.adapters.inbound.rest.dto;

import java.io.Serializable;

public record StagePayload(String stageId, String status, String label, String reason) implements Serializable {
}
