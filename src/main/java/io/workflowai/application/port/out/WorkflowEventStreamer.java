package io.workflowai.application.port.out;

import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.WorkflowEvent;
import io.workflowai.domain.workflow.StageId;

import java.util.UUID;
import java.util.function.Consumer;

public interface WorkflowEventStreamer {

    void stageStarted(UUID runId, StageId stageId);

    void stageCompleted(UUID runId, StageId stageId);

    void stageFailed(UUID runId, StageId stageId, String reason);

    void decisionMade(UUID runId, RoutingDecision decision);

    void token(UUID runId, String token);

    void responseCompleted(UUID runId, String finalResponse);

    void conversationCompleted(UUID runId);

    void registerConsumer(UUID runId, Consumer<WorkflowEvent> eventConsumer);

    void revokeConsumer(UUID runId);
}
