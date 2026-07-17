package io.workflowai.adapters.inbound.rest.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID agentId,
        String title,
        Instant createdAt,
        Instant updatedAt) implements Serializable {
}
