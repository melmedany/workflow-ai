package io.workflowai.adapter.in.rest.dto;

import java.io.Serializable;

public record ErrorPayload(String message) implements Serializable {
}