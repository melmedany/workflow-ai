package io.workflowai.adapters.inbound.rest.dto;

import io.workflowai.domain.model.ConversationMessageRole;

import java.io.Serializable;

public record MessageResponse(ConversationMessageRole role, String content) implements Serializable {
}
