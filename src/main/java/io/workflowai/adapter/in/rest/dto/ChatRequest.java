package io.workflowai.adapter.in.rest.dto;

import java.io.Serializable;

public record ChatRequest(String message) implements Serializable {
}
