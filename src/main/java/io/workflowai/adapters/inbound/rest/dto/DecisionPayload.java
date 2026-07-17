package io.workflowai.adapters.inbound.rest.dto;

import java.io.Serializable;

public record DecisionPayload(String mode, String reason) implements Serializable {
}
