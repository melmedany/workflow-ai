package io.workflowai.ports.outbound;

import io.workflowai.domain.model.TriggerSource;

import java.util.UUID;

public interface RunHistoryPort {

    UUID start(TriggerSource triggerSource, UUID agentId, UUID conversationId);

    void complete(UUID runId);

    void fail(UUID runId, String errorMessage);
}
