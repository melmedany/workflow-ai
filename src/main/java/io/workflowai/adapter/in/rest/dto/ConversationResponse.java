package io.workflowai.adapter.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID agentId,
        String title,
        Instant createdAt,
        Instant updatedAt) {
}
