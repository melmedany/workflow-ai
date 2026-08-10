package io.workflowai.adapter.in.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID agentId,
        UUID conversationId,
        String name,
        String cronExpression,
        Instant runOnceAt,
        String status,
        Instant lastRunAt,
        String lastRunStatus,
        Instant nextRunAt) {
}