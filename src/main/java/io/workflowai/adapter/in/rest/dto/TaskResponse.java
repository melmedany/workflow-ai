package io.workflowai.adapter.in.rest.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID agentId,
        UUID conversationId,
        String name,
        String scheduleType,
        Duration duration,
        String status,
        Instant lastRunAt,
        String lastRunStatus,
        Instant nextRunAt) {
}