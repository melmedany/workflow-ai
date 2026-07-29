package io.workflowai.adapter.out.stream;

import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.WorkflowEvent;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Stream workflow events to the active REST endpoint requests.
 */
@Component
public class SSEWorkflowEventStreamer implements WorkflowEventStreamer {

    private final DefaultStageLabelProvider labelProvider;

    /**
     * Simple in-memory consumer store keyed by runId, NOT conversationId. Two concurrent invocations for the
     * same conversation (double-submit, caller-side retry, etc.) must not share a consumer —
     * keying by conversationId previously let one run's consumer be overwritten/removed by
     * another run of the same conversation, causing cross-talk or a NoSuchElement on consumer(state).
     */
    private final ConcurrentHashMap<UUID, Consumer<WorkflowEvent>> activeConsumers = new ConcurrentHashMap<>();

    public SSEWorkflowEventStreamer() {
        this.labelProvider = new DefaultStageLabelProvider();
    }

    @Override
    public void stageStarted(UUID runId, StageId stageId) {
        Consumer<WorkflowEvent> events = consumer(runId);
        events.accept(new WorkflowEvent.StageStarted(stageId, labelProvider.started(stageId)));
    }

    @Override
    public void stageCompleted(UUID runId, StageId stageId) {
        Consumer<WorkflowEvent> events = consumer(runId);
        events.accept(new WorkflowEvent.StageCompleted(stageId, labelProvider.completed(stageId)));
    }

    @Override
    public void stageFailed(UUID runId, StageId stageId, String reason) {
        Consumer<WorkflowEvent> events = consumer(runId);
        events.accept(new WorkflowEvent.StageFailed(stageId, labelProvider.failed(stageId), reason));
    }

    @Override
    public void decisionMade(UUID runId, RoutingDecision decision) {
        Consumer<WorkflowEvent> events = consumer(runId);
        events.accept(new WorkflowEvent.DecisionMade(decision.decisionMode(), decision.reason()));
    }

    @Override
    public void token(UUID runId, String finalResponse) {
        Consumer<WorkflowEvent> events = consumer(runId);
        for (String token : finalResponse.split("(?<=\\s)")) {
            events.accept(new WorkflowEvent.Token(token));
        }
    }

    @Override
    public void responseCompleted(UUID runId, String finalResponse) {
        Consumer<WorkflowEvent> events = consumer(runId);
        events.accept(new WorkflowEvent.ResponseCompleted(finalResponse));
    }

    @Override
    public void conversationCompleted(UUID runId) {
        Consumer<WorkflowEvent> events = consumer(runId);
        events.accept(new WorkflowEvent.ConversationCompleted());
    }

    @Override
    public void registerConsumer(UUID runId, Consumer<WorkflowEvent> eventConsumer) {
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
    private Consumer<WorkflowEvent> consumer(UUID runId) {
        return Optional.ofNullable(activeConsumers.get(runId))
                .orElseThrow(() -> new IllegalStateException(
                        "No event consumer registered for runId in state. " +
                                "This usually means the state was not initialised with a runId or the consumer was already removed."));
    }
}
