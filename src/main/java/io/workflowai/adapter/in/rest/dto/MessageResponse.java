package io.workflowai.adapter.in.rest.dto;

import io.workflowai.domain.conversation.ConversationMessageRole;

public record MessageResponse(ConversationMessageRole role, String content) {
}
