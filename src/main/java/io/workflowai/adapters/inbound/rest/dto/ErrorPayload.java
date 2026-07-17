package io.workflowai.adapters.inbound.rest.dto;

import java.io.Serializable;

public record ErrorPayload(String message) implements Serializable {
}