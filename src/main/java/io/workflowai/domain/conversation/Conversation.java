package io.workflowai.domain.conversation;

import java.time.Instant;
import java.util.UUID;

public record Conversation(UUID id, UUID agentId, String title, Instant createdAt, Instant updatedAt) {
}
