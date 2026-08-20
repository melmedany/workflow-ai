package io.workflowai.application.execution.stage;

import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.exceptions.InvalidScheduleException;
import io.workflowai.domain.exceptions.ScheduleTooFrequentException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.workflowai.domain.agent.TriggerSource.SYSTEM_TRIGGER;
import static io.workflowai.domain.agent.TriggerSource.USER_MESSAGE;
import static io.workflowai.domain.task.ConversationTask.TaskDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateTaskStageTest {

    private final TaskUseCase taskUseCase = mock();
    private final ConversationMessageStorage messages = mock();
    private final WorkflowEventStreamer streamer = mock();

    private final CreateTaskStage stage = stage();

    @Test
    void createsTaskAndConfirmsInPlainLanguage() {
        ConversationTask task = mock();
        when(task.definition()).thenReturn(
                new TaskDefinition("name", "intentKey", "Summarize open PRs"));

        when(taskUseCase.createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("Summarize open PRs"),
                eq(ScheduleType.RECURRING),
                any(Instant.class),
                eq("P1D")))
                .thenReturn(task);

        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE,
                executeDecision(Instant.now().toString(), "P1D", "Summarize open PRs")));

        verify(taskUseCase).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("Summarize open PRs"),
                eq(ScheduleType.RECURRING),
                any(Instant.class),
                eq("P1D"));
        verify(messages).save(
                any(UUID.class),
                any(UUID.class),
                argThat(message -> message.content().contains("Summarize open PRs")));

        assertThat(result).containsEntry(WorkflowState.KEY_VALIDATION_PASSED, true);
    }

    @Test
    void systemTriggerCanNeverCreateATask() {
        Map<String, Object> result = stage.execute(
                StagesUtil.state(
                        SYSTEM_TRIGGER,
                        executeDecision(Instant.now().toString(), "P1D", "Summarize open PRs")));

        verifyNoInteractions(taskUseCase);

        assertThat(decisionMode(result))
                .isEqualTo(DecisionMode.REFUSE);
    }

    @Test
    void createsTaskWhenExtractedInstructionStillLooksLikeSchedulingRequest() {
        ConversationTask task = mock();
        when(task.definition()).thenReturn(
                new TaskDefinition("name", "intentKey", "every hour, ping me"));

        when(taskUseCase.createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("every hour, ping me"),
                eq(ScheduleType.RECURRING),
                any(Instant.class),
                eq("P1D")))
                .thenReturn(task);

        Map<String, Object> result = stage.execute(
                StagesUtil.state(
                        USER_MESSAGE,
                        executeDecision(Instant.now().toString(), "P1D", "every hour, ping me")));

        verify(taskUseCase).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("every hour, ping me"),
                eq(ScheduleType.RECURRING),
                any(Instant.class),
                eq("P1D"));

        assertThat(result)
                .containsKey(WorkflowState.KEY_GENERATED_RESPONSE)
                .extractingByKey(WorkflowState.KEY_GENERATED_RESPONSE)
                .isNotNull();
    }

    @Test
    void clarifiesWhenScheduleCannotBeParsed() {
        when(taskUseCase.createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                anyString(),
                any(ScheduleType.class),
                any(Instant.class),
                anyString()))
                .thenThrow(new InvalidScheduleException("bad duration"));

        Map<String, Object> result = stage.execute(
                StagesUtil.state(
                        USER_MESSAGE,
                        executeDecision(Instant.now().toString(), "not-a-duration", "Summarize open PRs")));

        verify(taskUseCase, never()).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("Summarize open PRs"),
                eq(ScheduleType.RECURRING),
                any(Instant.class),
                eq("P1D"));

        assertThat(decisionMode(result))
                .isEqualTo(DecisionMode.CLARIFY);

        assertThat(result)
                .extractingByKey(WorkflowState.KEY_ROUTING_DECISION)
                .isInstanceOfSatisfying(
                        RoutingDecision.class,
                        decision -> assertThat(
                                decision.clarificationQuestion())
                                .isNotBlank());
    }

    @Test
    void refusesWhenScheduleIsTooFrequent() {
        when(taskUseCase.createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                anyString(),
                any(ScheduleType.class),
                any(Instant.class),
                anyString()))
                .thenThrow(new ScheduleTooFrequentException("too frequent"));

        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE,
                executeDecision(Instant.now().toString(), "PT1S", "Summarize open PRs")));

        assertThat(decisionMode(result))
                .isEqualTo(DecisionMode.REFUSE);

        verify(taskUseCase).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                anyString(),
                eq(ScheduleType.RECURRING),
                any(Instant.class),
                eq("PT1S"));
    }

    @Test
    void refusesWhenScheduleInstructionIsMissing() {
        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE,
                executeDecision(Instant.now().toString(), "P1D", null)));

        verifyNoInteractions(taskUseCase);
        assertThat(decisionMode(result)).isEqualTo(DecisionMode.REFUSE);
    }

    @Test
    void refusesWhenInstructionCleansDownToNothing() {
        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE,
                executeDecision(Instant.now().toString(), "P1D", "remind me")));

        verifyNoInteractions(taskUseCase);
        assertThat(decisionMode(result)).isEqualTo(DecisionMode.REFUSE);
    }

    @Test
    void createsOnceScheduleTypeTask() {
        ConversationTask task = mock();
        when(task.definition()).thenReturn(
                new TaskDefinition("name", "intentKey", "Send a one-off summary"));

        when(taskUseCase.createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("Send a one-off summary"),
                eq(ScheduleType.ONCE),
                any(Instant.class),
                eq("PT30M")))
                .thenReturn(task);

        RoutingDecision decision = new RoutingDecision(
                DecisionMode.EXECUTE, List.of(), "schedule request", null, "clear one-off request",
                ScheduleType.ONCE.name(), Instant.now().toString(), "PT30M", "Send a one-off summary");

        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE, decision));

        verify(taskUseCase).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("Send a one-off summary"),
                eq(ScheduleType.ONCE),
                any(Instant.class),
                eq("PT30M"));

        assertThat(result).containsEntry(WorkflowState.KEY_VALIDATION_PASSED, true);
    }

    @Test
    void emitsStageStartedAndStageCompletedOnSuccess() {
        ConversationTask task = mock();
        when(task.definition()).thenReturn(new TaskDefinition("name", "intentKey", "Summarize open PRs"));
        when(taskUseCase.createOrUpdate(any(UUID.class), any(UUID.class), anyString(),
                any(ScheduleType.class), any(Instant.class), anyString())).thenReturn(task);

        WorkflowState state = StagesUtil.state(USER_MESSAGE,
                executeDecision(Instant.now().toString(), "P1D", "Summarize open PRs"));

        stage.execute(state);

        verify(streamer).stageStarted(state.runId(), StageId.CREATE_TASK);
        verify(streamer).stageCompleted(state.runId(), StageId.CREATE_TASK);
    }

    @Test
    void emitsStageStartedAndStageCompletedOnSystemTriggerRefusal() {
        WorkflowState state = StagesUtil.state(SYSTEM_TRIGGER,
                executeDecision(Instant.now().toString(), "P1D", "Summarize open PRs"));

        stage.execute(state);

        verify(streamer).stageStarted(state.runId(), StageId.CREATE_TASK);
        verify(streamer).stageCompleted(state.runId(), StageId.CREATE_TASK);
    }

    @Test
    void clarifiesInsteadOfCrashingWhenScheduleTypeIsMissing() {
        RoutingDecision decision = new RoutingDecision(
                DecisionMode.EXECUTE, List.of(), "schedule request", null, "clear recurring request",
                null, Instant.now().toString(), "P1D", "Summarize open PRs");

        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE, decision));

        assertThat(decisionMode(result)).isEqualTo(DecisionMode.CLARIFY);
    }

    @Test
    void clarifiesInsteadOfCrashingWhenStartDateTimeIsMissing() {
        RoutingDecision decision = new RoutingDecision(
                DecisionMode.EXECUTE, List.of(), "schedule request", null, "clear recurring request",
                ScheduleType.RECURRING.name(), null, "P1D", "Summarize open PRs");

        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE, decision));

        assertThat(decisionMode(result)).isEqualTo(DecisionMode.CLARIFY);
    }

    private CreateTaskStage stage() {
        return new CreateTaskStage(
                taskUseCase,
                new PersistResponseStage(messages, List.of()),
                List.of(streamer));
    }

    private DecisionMode decisionMode(Map<String, Object> result) {
        return ((RoutingDecision) result
                .get(WorkflowState.KEY_ROUTING_DECISION))
                .decisionMode();
    }

    private RoutingDecision executeDecision(String startDateTime, String duration, String scheduleInstruction) {
        return new RoutingDecision(
                DecisionMode.EXECUTE,
                List.of(),
                "schedule request",
                null,
                "clear recurring request",
                ScheduleType.RECURRING.name(),
                startDateTime,
                duration,
                scheduleInstruction);
    }
}