package io.workflowai.application.port.out;

import io.workflowai.domain.agent.TriggerSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AgentRunTracker {

    UUID start(TriggerSource triggerSource, UUID agentId, UUID conversationId, UUID taskId);

    void complete(UUID runId);

    void fail(UUID runId, String errorMessage);

    Optional<AgentRunSummary> find(UUID runId);

    record AgentRunSummary(UUID id, String status, Instant completedAt, String errorMessage) {
    }
}
