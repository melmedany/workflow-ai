package io.workflowai.ports.outbound;

import io.workflowai.domain.model.RoutingDecision;
import io.workflowai.domain.workflow.PipelineEvent;
import io.workflowai.domain.workflow.StageId;

import java.util.UUID;
import java.util.function.Consumer;

public interface PipelineEventStreamer {

    void stageStarted(UUID runId, StageId stageId);

    void stageCompleted(UUID runId, StageId stageId);

    void stageFailed(UUID runId, StageId stageId, String reason);

    void decisionMade(UUID runId, RoutingDecision decision);

    void token(UUID runId, String token);

    void responseCompleted(UUID runId, String finalResponse);

    void conversationCompleted(UUID runId);

    void registerConsumer(UUID runId, Consumer<PipelineEvent> eventConsumer);

    void revokeConsumer(UUID runId);
}
