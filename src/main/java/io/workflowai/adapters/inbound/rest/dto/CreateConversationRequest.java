package io.workflowai.adapters.inbound.rest.dto;

import java.io.Serializable;

public record CreateConversationRequest(String firstMessage) implements Serializable {
}
