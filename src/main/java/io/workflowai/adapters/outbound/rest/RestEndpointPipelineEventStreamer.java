package io.workflowai.adapters.outbound.rest;

import io.workflowai.adapters.DefaultStageLabelProvider;
import io.workflowai.domain.model.RoutingDecision;
import io.workflowai.domain.workflow.PipelineEvent;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.ports.outbound.PipelineEventStreamer;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Stream pipeline events to the active REST endpoint requests.
 */
@Component
public class RestEndpointPipelineEventStreamer implements PipelineEventStreamer {

    private final DefaultStageLabelProvider labelProvider;

    /**
     * Simple in-memory consumer store keyed by runId, NOT conversationId. Two concurrent invocations for the
     * same conversation (double-submit, caller-side retry, etc.) must not share a consumer —
     * keying by conversationId previously let one run's consumer be overwritten/removed by
     * another run of the same conversation, causing cross-talk or a NoSuchElement on consumer(state).
     */
    private final ConcurrentHashMap<UUID, Consumer<PipelineEvent>> activeConsumers = new ConcurrentHashMap<>();

    public RestEndpointPipelineEventStreamer() {
        this.labelProvider = new DefaultStageLabelProvider();
    }

    @Override
    public void stageStarted(UUID runId, StageId stageId) {
        Consumer<PipelineEvent> events = consumer(runId);
        events.accept(new PipelineEvent.StageStarted(stageId, labelProvider.started(stageId)));
    }

    @Override
    public void stageCompleted(UUID runId, StageId stageId) {
        Consumer<PipelineEvent> events = consumer(runId);
        events.accept(new PipelineEvent.StageCompleted(stageId, labelProvider.completed(stageId)));
    }

    @Override
    public void stageFailed(UUID runId, StageId stageId, String reason) {
        Consumer<PipelineEvent> events = consumer(runId);
        events.accept(new PipelineEvent.StageFailed(stageId, labelProvider.failed(stageId), reason));
    }

    @Override
    public void decisionMade(UUID runId, RoutingDecision decision) {
        Consumer<PipelineEvent> events = consumer(runId);
        events.accept(new PipelineEvent.DecisionMade(decision.decisionMode(), decision.reason()));
    }

    @Override
    public void token(UUID runId, String finalResponse) {
        Consumer<PipelineEvent> events = consumer(runId);
        for (String token : finalResponse.split("(?<=\\s)")) {
            events.accept(new PipelineEvent.Token(token));
        }
    }

    @Override
    public void responseCompleted(UUID runId, String finalResponse) {
        Consumer<PipelineEvent> events = consumer(runId);
        events.accept(new PipelineEvent.ResponseCompleted(finalResponse));
    }

    @Override
    public void conversationCompleted(UUID runId) {
        Consumer<PipelineEvent> events = consumer(runId);
        events.accept(new PipelineEvent.ConversationCompleted());
    }

    @Override
    public void registerConsumer(UUID runId, Consumer<PipelineEvent> eventConsumer) {
        activeConsumers.put(runId, eventConsumer);
    }

    @Override
    public void revokeConsumer(UUID runId) {
        activeConsumers.remove(runId);
    }

    /**
     * Retrieves the event consumer registered for the given workflow state.
     * Called by node implementations to emit events during graph execution.
     */
    private Consumer<PipelineEvent> consumer(UUID runId) {
        return Optional.ofNullable(activeConsumers.get(runId))
                .orElseThrow(() -> new IllegalStateException(
                        "No event consumer registered for runId in state. " +
                                "This usually means the state was not initialised with a runId or the consumer was already removed."));
    }
}
