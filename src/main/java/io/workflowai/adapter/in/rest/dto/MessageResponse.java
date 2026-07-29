package io.workflowai.adapter.in.rest.dto;

import io.workflowai.domain.conversation.ConversationMessageRole;

import java.io.Serializable;

public record MessageResponse(ConversationMessageRole role, String content) implements Serializable {
}
