package io.workflowai.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Conversation(UUID id, UUID agentId, String title, Instant createdAt, Instant updatedAt) {
}
