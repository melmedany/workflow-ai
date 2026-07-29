package io.workflowai.application.port.out;

import io.workflowai.domain.run.TriggerSource;

import java.util.UUID;

public interface AgentRunTracker {

    UUID start(TriggerSource triggerSource, UUID agentId, UUID conversationId);

    void complete(UUID runId);

    void fail(UUID runId, String errorMessage);
}
