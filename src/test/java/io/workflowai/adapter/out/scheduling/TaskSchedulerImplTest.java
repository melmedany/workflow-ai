package io.workflowai.adapter.out.scheduling;

import io.workflowai.domain.exceptions.ScheduleTooFrequentException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskDefinition;
import io.workflowai.domain.task.ConversationTask.TaskRunInfo;
import io.workflowai.domain.task.ConversationTask.TaskSchedule;
import io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType;
import io.workflowai.domain.task.TaskStatus;
import org.jobrunr.jobs.JobId;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskSchedulerImplTest {

    private final JobScheduler jobScheduler = mock();
    private final TaskSchedulerImpl scheduler = new TaskSchedulerImpl(jobScheduler);

    private final UUID agentId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void tooFrequentDurationIsRejectedBeforeTouchingJobRunr() {
        ConversationTask task = recurringTask(anchorAt(10, 7), "PT30S");

        assertThatThrownBy(() -> scheduler.schedule(task)).isInstanceOf(ScheduleTooFrequentException.class);

        verifyNoJobRunrScheduling();
    }

    @Test
    void subHourIntervalDividingSixtyIsAnchoredToTheStartMinute() {
        ConversationTask task = recurringTask(anchorAt(10, 7), "PT15M");

        scheduler.schedule(task);

        verify(jobScheduler).<ScheduledAgentTaskRunner>scheduleRecurrently(eq(task.id().toString()),
                eq("7,22,37,52 * * * *"), any(IocJobLambda.class));
    }

    @Test
    void subHourIntervalStartingOnTheHourMatchesThePlainStepPattern() {
        ConversationTask task = recurringTask(anchorAt(10, 0), "PT5M");

        scheduler.schedule(task);

        verify(jobScheduler).<ScheduledAgentTaskRunner>scheduleRecurrently(eq(task.id().toString()),
                eq("0,5,10,15,20,25,30,35,40,45,50,55 * * * *"), any(IocJobLambda.class));
    }

    @Test
    void subHourIntervalNotDividingSixtyIsRejected() {
        ConversationTask task = recurringTask(anchorAt(10, 7), "PT7M");

        assertThatThrownBy(() -> scheduler.schedule(task)).isInstanceOf(IllegalArgumentException.class);

        verifyNoJobRunrScheduling();
    }

    @Test
    void hourMultipleIntervalIsAnchoredToStartMinuteAndHour() {
        ConversationTask task = recurringTask(anchorAt(9, 30), "PT2H");

        scheduler.schedule(task);

        verify(jobScheduler).<ScheduledAgentTaskRunner>scheduleRecurrently(eq(task.id().toString()),
                eq("30 11-23/2 * * *"), any(IocJobLambda.class));
    }

    @Test
    void twentyFourHourIntervalFiresOnceDailyAtTheAnchor() {
        ConversationTask task = recurringTask(anchorAt(9, 30), "PT24H");

        scheduler.schedule(task);

        verify(jobScheduler).<ScheduledAgentTaskRunner>scheduleRecurrently(eq(task.id().toString()),
                eq("30 9 * * *"), any(IocJobLambda.class));
    }

    @Test
    void nonWholeHourDurationOverAnHourIsRejected() {
        ConversationTask task = recurringTask(anchorAt(9, 30), "PT90M");

        assertThatThrownBy(() -> scheduler.schedule(task)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weeklyPeriodIsAnchoredToTheStartDayOfWeek() {
        Instant wednesday = anchorAt(9, 0).withDayOfMonth(5).toInstant(); // 2026-08-05 is a Wednesday
        ConversationTask task = recurringTask(wednesday, "P7D");

        scheduler.schedule(task);

        verify(jobScheduler).<ScheduledAgentTaskRunner>scheduleRecurrently(eq(task.id().toString()),
                eq("0 9 * * 3"), any(IocJobLambda.class));
    }

    @Test
    void unsupportedPeriodShapeIsRejected() {
        ConversationTask task = recurringTask(anchorAt(9, 0), "P3D");

        assertThatThrownBy(() -> scheduler.schedule(task)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onceTaskSchedulesAOneOffJobKeyedByTaskId() {
        Instant createdAt = Instant.parse("2026-08-10T09:00:00Z");
        ConversationTask task = onceTask(createdAt, "PT45M");

        when(jobScheduler.<ScheduledAgentTaskRunner>schedule(eq(task.id()), any(Instant.class), any(IocJobLambda.class)))
                .thenReturn(new JobId(task.id()));

        String jobId = scheduler.schedule(task);

        assertThat(jobId).isEqualTo(task.id().toString());
        verify(jobScheduler).<ScheduledAgentTaskRunner>schedule(eq(task.id()),
                eq(createdAt.plus(Duration.ofMinutes(45))), any(IocJobLambda.class));
    }

    @Test
    void rescheduleDelegatesToSchedule() {
        ConversationTask task = recurringTask(anchorAt(9, 0), "P1D");

        scheduler.reschedule(task);

        verify(jobScheduler).<ScheduledAgentTaskRunner>scheduleRecurrently(eq(task.id().toString()),
                anyString(), any(IocJobLambda.class));
    }

    @Test
    void pauseDelegatesToCancel() {
        ConversationTask task = recurringTask(anchorAt(9, 0), "P1D");

        scheduler.pause(task);

        verify(jobScheduler).deleteRecurringJob(task.runInfo().jobId());
    }

    @Test
    void resumeDelegatesToSchedule() {
        ConversationTask task = recurringTask(anchorAt(9, 0), "P1D");

        scheduler.resume(task);

        verify(jobScheduler).<ScheduledAgentTaskRunner>scheduleRecurrently(eq(task.id().toString()),
                anyString(), any(IocJobLambda.class));
    }

    @Test
    void cancelDeletesTheOneOffJobForAOnceTask() {
        ConversationTask task = onceTask(Instant.now(), "PT45M");

        scheduler.cancel(task);

        verify(jobScheduler).delete(UUID.fromString(task.runInfo().jobId()));
    }

    @Test
    void cancelDeletesTheRecurringJobForARecurringTask() {
        ConversationTask task = recurringTask(anchorAt(9, 0), "P1D");

        scheduler.cancel(task);

        verify(jobScheduler).deleteRecurringJob(task.runInfo().jobId());
    }

    private void verifyNoJobRunrScheduling() {
        verify(jobScheduler, never()).<ScheduledAgentTaskRunner>scheduleRecurrently(anyString(), anyString(), any(IocJobLambda.class));
        verify(jobScheduler, never()).<ScheduledAgentTaskRunner>schedule(any(UUID.class), any(Instant.class), any(IocJobLambda.class));
    }

    private static ZonedDateTime anchorAt(int hour, int minute) {
        return ZonedDateTime.of(2026, 8, 10, hour, minute, 0, 0, ZoneId.systemDefault());
    }

    private ConversationTask recurringTask(ZonedDateTime anchor, String duration) {
        return recurringTask(anchor.toInstant(), duration);
    }

    private ConversationTask recurringTask(Instant anchor, String duration) {
        return ConversationTask.newTask(agentId, conversationId,
                new TaskDefinition("Summarize open PRs", "intent-key", "Summarize open PRs"),
                new TaskSchedule(ScheduleType.RECURRING, anchor, duration, TaskStatus.ACTIVE),
                new TaskRunInfo(UUID.randomUUID().toString(), null, null));
    }

    private ConversationTask onceTask(Instant createdAt, String duration) {
        UUID id = UUID.randomUUID();
        return new ConversationTask(id, agentId, conversationId,
                new TaskDefinition("Send report", "intent-key", "Send report"),
                new TaskSchedule(ScheduleType.ONCE, createdAt, duration, TaskStatus.ACTIVE),
                new TaskRunInfo(id.toString(), null, null),
                createdAt, createdAt);
    }
}
