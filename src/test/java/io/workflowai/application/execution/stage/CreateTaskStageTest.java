package io.workflowai.application.execution.stage;

import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.domain.exceptions.InvalidScheduleException;
import io.workflowai.domain.exceptions.ScheduleTooFrequentException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateTaskStageTest {

    private final TaskUseCase taskUseCase = mock();
    private final ConversationMessageStorage messages = mock();

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
                eq(Duration.ofDays(1))))
                .thenReturn(task);

        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE,
                executeDecision("P1D", "Summarize open PRs")));

        verify(taskUseCase).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("Summarize open PRs"),
                eq(ScheduleType.RECURRING),
                eq(Duration.ofDays(1)));

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
                        executeDecision("P1D", "Summarize open PRs")));

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
                eq(Duration.ofDays(1))))
                .thenReturn(task);

        Map<String, Object> result = stage.execute(
                StagesUtil.state(
                        USER_MESSAGE,
                        executeDecision(
                                "P1D",
                                "every hour, ping me")));

        verify(taskUseCase).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("every hour, ping me"),
                eq(ScheduleType.RECURRING),
                eq(Duration.ofDays(1)));

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
                any(Duration.class)))
                .thenThrow(new InvalidScheduleException("bad duration"));

        Map<String, Object> result = stage.execute(
                StagesUtil.state(
                        USER_MESSAGE,
                        executeDecision(
                                "not-a-duration",
                                "Summarize open PRs")));

        verify(taskUseCase, times(0)).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                eq("Summarize open PRs"),
                eq(ScheduleType.RECURRING),
                eq(Duration.ofDays(1)));

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
                any(Duration.class)))
                .thenThrow(new ScheduleTooFrequentException("too frequent"));

        Map<String, Object> result = stage.execute(StagesUtil.state(USER_MESSAGE,
                executeDecision("PT1S", "Summarize open PRs")));

        assertThat(decisionMode(result))
                .isEqualTo(DecisionMode.REFUSE);

        verify(taskUseCase).createOrUpdate(
                any(UUID.class),
                any(UUID.class),
                anyString(),
                eq(ScheduleType.RECURRING),
                eq(Duration.ofSeconds(1)));
    }

    private CreateTaskStage stage() {
        return new CreateTaskStage(
                taskUseCase,
                new PersistResponseStage(messages, List.of()),
                List.of());
    }

//    private ConversationTask task() {
//        return mock();
//    }

    private DecisionMode decisionMode(Map<String, Object> result) {
        return ((RoutingDecision) result
                .get(WorkflowState.KEY_ROUTING_DECISION))
                .decisionMode();
    }

    private RoutingDecision executeDecision(
            String duration,
            String scheduleInstruction) {

        return new RoutingDecision(
                DecisionMode.EXECUTE,
                List.of(),
                "schedule request",
                null,
                "clear recurring request",
                ScheduleType.RECURRING.name(),
                duration,
                scheduleInstruction);
    }
}