package io.workflowai.adapters.inbound.rest.dto;

import java.io.Serializable;

public record ChatRequest(String message) implements Serializable {
}
