package io.workflowai.adapter.out.stream;

import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SSEWorkflowEventStreamerTest {

    private final SSEWorkflowEventStreamer streamer = new SSEWorkflowEventStreamer();

    @Test
    void stageStartedDeliversAStageStartedEventToTheRegisteredConsumer() {
        UUID runId = UUID.randomUUID();
        List<WorkflowEvent> received = new ArrayList<>();
        streamer.registerConsumer(runId, received::add);

        streamer.stageStarted(runId, StageId.CLASSIFICATION);

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()).isInstanceOf(WorkflowEvent.StageStarted.class);
        WorkflowEvent.StageStarted event = (WorkflowEvent.StageStarted) received.getFirst();
        assertThat(event.stageId()).isEqualTo(StageId.CLASSIFICATION);
    }

    @Test
    void stageFailedDeliversTheReasonVerbatim() {
        UUID runId = UUID.randomUUID();
        List<WorkflowEvent> received = new ArrayList<>();
        streamer.registerConsumer(runId, received::add);

        streamer.stageFailed(runId, StageId.SELF_VERIFICATION, "retry still invalid");

        WorkflowEvent.StageFailed event = (WorkflowEvent.StageFailed) received.getFirst();
        assertThat(event.reason()).isEqualTo("retry still invalid");
        assertThat(event.label()).contains("failed");
    }

    @Test
    void decisionMadeForwardsOnlyModeAndReason() {
        UUID runId = UUID.randomUUID();
        List<WorkflowEvent> received = new ArrayList<>();
        streamer.registerConsumer(runId, received::add);

        streamer.decisionMade(runId, RoutingDecision.refuse("out of scope", "hack the mainframe"));

        WorkflowEvent.DecisionMade event = (WorkflowEvent.DecisionMade) received.getFirst();
        assertThat(event.mode()).isEqualTo(DecisionMode.REFUSE);
        assertThat(event.reason()).isEqualTo("out of scope");
    }

    @Test
    void tokenSplitsOnWhitespaceKeepingItAttachedToThePrecedingWord() {
        UUID runId = UUID.randomUUID();
        List<WorkflowEvent> received = new ArrayList<>();
        streamer.registerConsumer(runId, received::add);

        streamer.token(runId, "hello world");

        List<String> tokens = received.stream().map(e -> ((WorkflowEvent.Token) e).token()).toList();
        assertThat(tokens).containsExactly("hello ", "world");
    }

    @Test
    void tokenWithEmptyStringEmitsExactlyOneEmptyToken() {
        UUID runId = UUID.randomUUID();
        List<WorkflowEvent> received = new ArrayList<>();
        streamer.registerConsumer(runId, received::add);

        streamer.token(runId, "");

        assertThat(received).hasSize(1);
        assertThat(((WorkflowEvent.Token) received.getFirst()).token()).isEmpty();
    }

    @Test
    void eventsForDifferentRunsNeverCrossOver() {
        UUID runA = UUID.randomUUID();
        UUID runB = UUID.randomUUID();
        List<WorkflowEvent> receivedA = new ArrayList<>();
        List<WorkflowEvent> receivedB = new ArrayList<>();
        streamer.registerConsumer(runA, receivedA::add);
        streamer.registerConsumer(runB, receivedB::add);

        streamer.stageStarted(runA, StageId.CLASSIFICATION);
        streamer.stageStarted(runB, StageId.EXECUTE_WORKFLOW);

        assertThat(receivedA).hasSize(1);
        assertThat(receivedB).hasSize(1);
        assertThat(((WorkflowEvent.StageStarted) receivedA.getFirst()).stageId()).isEqualTo(StageId.CLASSIFICATION);
        assertThat(((WorkflowEvent.StageStarted) receivedB.getFirst()).stageId()).isEqualTo(StageId.EXECUTE_WORKFLOW);
    }

    @Test
    void emittingForAnUnregisteredRunThrowsIllegalStateException() {
        UUID runId = UUID.randomUUID();

        assertThatThrownBy(() -> streamer.stageStarted(runId, StageId.CLASSIFICATION))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emittingAfterRevokeThrowsIllegalStateException() {
        UUID runId = UUID.randomUUID();
        streamer.registerConsumer(runId, _ -> {
        });
        streamer.revokeConsumer(runId);

        assertThatThrownBy(() -> streamer.stageCompleted(runId, StageId.CLASSIFICATION))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reRegisteringReplacesThePreviousConsumer() {
        UUID runId = UUID.randomUUID();
        List<WorkflowEvent> oldConsumerEvents = new ArrayList<>();
        List<WorkflowEvent> newConsumerEvents = new ArrayList<>();
        streamer.registerConsumer(runId, oldConsumerEvents::add);
        streamer.registerConsumer(runId, newConsumerEvents::add);

        streamer.stageStarted(runId, StageId.CLASSIFICATION);

        assertThat(oldConsumerEvents).isEmpty();
        assertThat(newConsumerEvents).hasSize(1);
    }

    @Test
    void conversationCompletedDeliversTheEvent() {
        UUID runId = UUID.randomUUID();
        List<WorkflowEvent> received = new ArrayList<>();
        streamer.registerConsumer(runId, received::add);

        streamer.conversationCompleted(runId);

        assertThat(received.getFirst()).isInstanceOf(WorkflowEvent.ConversationCompleted.class);
    }

    @Test
    void responseCompletedDeliversTheFinalResponse() {
        UUID runId = UUID.randomUUID();
        List<WorkflowEvent> received = new ArrayList<>();
        streamer.registerConsumer(runId, received::add);

        streamer.responseCompleted(runId, "final answer");

        WorkflowEvent.ResponseCompleted event = (WorkflowEvent.ResponseCompleted) received.getFirst();
        assertThat(event.fullResponse()).isEqualTo("final answer");
    }
}
