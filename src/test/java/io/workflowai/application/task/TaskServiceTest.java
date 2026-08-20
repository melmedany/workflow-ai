package io.workflowai.application.task;

import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.application.port.out.TaskScheduler;
import io.workflowai.domain.exceptions.TaskNotFoundException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskDefinition;
import io.workflowai.domain.task.ConversationTask.TaskRunInfo;
import io.workflowai.domain.task.ConversationTask.TaskSchedule;
import io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType;
import io.workflowai.domain.task.TaskStatus;
import org.jobrunr.storage.JobNotFoundException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private final ConversationTaskStorage storage = mock();
    private final TaskScheduler scheduler = mock();

    private final TaskService taskService = new TaskService(storage, scheduler);

    private final UUID agentId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void createsANewTaskWhenNoneExistYetForTheConversation() {
        when(storage.findByConversation(agentId, conversationId)).thenReturn(List.of());

        String jobId = UUID.randomUUID().toString();
        ConversationTask task = task("Summarize open PRs", TaskStatus.ACTIVE, jobId);

        when(scheduler.schedule(any(ConversationTask.class))).thenReturn(jobId);
        when(storage.create(any(ConversationTask.class))).thenReturn(task);

        ConversationTask result = taskService.createOrUpdate(agentId, conversationId, "Summarize open PRs",
                ScheduleType.RECURRING, Instant.now(), "P1D");

        verify(scheduler).schedule(any(ConversationTask.class));
        verify(storage).create(any(ConversationTask.class));
        assertThat(result.runInfo().jobId()).isEqualTo(jobId);
    }

    @Test
    void createsASecondIndependentTaskWhenExistingTasksHaveADifferentIntent() {
        ConversationTask existing = task("Summarize open PRs", TaskStatus.ACTIVE, UUID.randomUUID().toString());
        when(storage.findByConversation(agentId, conversationId)).thenReturn(List.of(existing));

        ConversationTask created = task("Send weekly digest", TaskStatus.ACTIVE, UUID.randomUUID().toString());
        when(storage.create(any(ConversationTask.class))).thenReturn(created);
        when(scheduler.schedule(created)).thenReturn("job-2");

        taskService.createOrUpdate(agentId, conversationId, "Send weekly digest",
                ScheduleType.RECURRING, Instant.now(), "P1D");

        verify(storage).create(any(ConversationTask.class));
        verify(storage, never()).update(any(ConversationTask.class));
    }

    @Test
    void updatesTheMatchingTaskWhenInstructionDiffersOnlyByCaseAndWhitespace() {
        ConversationTask existing = task("summarize open prs", TaskStatus.PAUSED, UUID.randomUUID().toString());
        when(storage.findByConversation(agentId, conversationId)).thenReturn(List.of(existing));

        ConversationTask updated = existing.update("  Summarize Open PRs  ", Instant.now(), "P2D");
        when(storage.update(any(ConversationTask.class))).thenReturn(updated, updated.withJobId("job-3"));
        when(scheduler.reschedule(any(ConversationTask.class))).thenReturn("job-3");

        taskService.createOrUpdate(agentId, conversationId, "  Summarize Open PRs  ",
                ScheduleType.RECURRING, Instant.now(), "P2D");

        verify(storage, never()).create(any(ConversationTask.class));
        verify(scheduler).reschedule(any(ConversationTask.class));
    }

    @Test
    void updatingAMatchingTaskPreservesItsExistingStatus() {
        ConversationTask existing = task("Summarize open PRs", TaskStatus.PAUSED, UUID.randomUUID().toString());
        when(storage.findByConversation(agentId, conversationId)).thenReturn(List.of(existing));
        when(storage.update(any(ConversationTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scheduler.reschedule(any(ConversationTask.class))).thenReturn("job-4");

        ConversationTask result = taskService.createOrUpdate(agentId, conversationId, "Summarize open PRs",
                ScheduleType.RECURRING, Instant.now(), "P2D");

        assertThat(result.schedule().status()).isEqualTo(TaskStatus.PAUSED);
    }

    @Test
    void pauseUpdatesStatusAndCallsScheduler() {
        ConversationTask task = task("Summarize open PRs", TaskStatus.ACTIVE, UUID.randomUUID().toString());
        when(storage.findTaskWithStatus(agentId, conversationId, task.id(), TaskStatus.ACTIVE)).thenReturn(Optional.of(task));

        taskService.pause(agentId, conversationId, task.id());

        verify(storage).updateStatus(agentId, conversationId, task.id(), TaskStatus.PAUSED);
        verify(scheduler).pause(any(ConversationTask.class));
    }

    @Test
    void resumeUpdatesStatusAndCallsScheduler() {
        ConversationTask task = task("Summarize open PRs", TaskStatus.PAUSED, UUID.randomUUID().toString());
        when(storage.findTaskWithStatus(agentId, conversationId, task.id(), TaskStatus.PAUSED)).thenReturn(Optional.of(task));

        taskService.resume(agentId, conversationId, task.id());

        verify(storage).updateStatus(agentId, conversationId, task.id(), TaskStatus.ACTIVE);
        verify(scheduler).resume(any(ConversationTask.class));
    }

    @Test
    void cancelUpdatesStatusAndCallsScheduler() {
        ConversationTask task = task("Summarize open PRs", TaskStatus.ACTIVE, UUID.randomUUID().toString());
        when(storage.findTask(agentId, conversationId, task.id())).thenReturn(Optional.of(task));

        taskService.cancel(agentId, conversationId, task.id());

        verify(storage).updateStatus(agentId, conversationId, task.id(), TaskStatus.CANCELLED);
        verify(scheduler).cancel(any(ConversationTask.class));
    }

    @Test
    void pauseThrowsTaskNotFoundExceptionAndNeverCallsSchedulerWhenTaskIsUnknown() {
        UUID unknownTaskId = UUID.randomUUID();
        when(storage.findTaskWithStatus(agentId, conversationId, unknownTaskId, TaskStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.pause(agentId, conversationId, unknownTaskId))
                .isInstanceOf(TaskNotFoundException.class);

        verify(scheduler, never()).pause(any());
        verify(storage, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void cancelAllContinuesAfterAJobNotFoundExceptionOnOneTask() {
        ConversationTask task1 = task("task one", TaskStatus.ACTIVE, UUID.randomUUID().toString());
        ConversationTask task2 = task("task two", TaskStatus.ACTIVE, UUID.randomUUID().toString());
        ConversationTask task3 = task("task three", TaskStatus.ACTIVE, UUID.randomUUID().toString());
        when(storage.findByConversation(agentId, conversationId)).thenReturn(List.of(task1, task2, task3));

        when(storage.findTaskWithStatus(agentId, conversationId, task1.id(), TaskStatus.ACTIVE)).thenReturn(Optional.of(task1));
        when(storage.findTaskWithStatus(agentId, conversationId, task2.id(), TaskStatus.ACTIVE)).thenReturn(Optional.of(task2));
        when(storage.findTaskWithStatus(agentId, conversationId, task3.id(), TaskStatus.ACTIVE)).thenReturn(Optional.of(task3));

        doThrow(new JobNotFoundException(task2.runInfo().jobId()))
                .when(scheduler).cancel(argThatMatches(task2.id()));

        taskService.cancelAll(agentId, conversationId);

        verify(storage).updateStatus(agentId, conversationId, task1.id(), TaskStatus.CANCELLED);
        verify(storage).updateStatus(agentId, conversationId, task3.id(), TaskStatus.CANCELLED);
    }

    @Test
    void listByConversationDelegatesDirectlyToStorage() {
        List<ConversationTask> tasks = List.of(task("a task", TaskStatus.ACTIVE, UUID.randomUUID().toString()));
        when(storage.findByConversation(agentId, conversationId)).thenReturn(tasks);

        List<ConversationTask> result = taskService.listByConversation(agentId, conversationId);

        assertThat(result).isEqualTo(tasks);
    }

    private ConversationTask argThatMatches(UUID taskId) {
        return argThat(t -> t != null && t.id().equals(taskId));
    }

    private ConversationTask task(String instruction, TaskStatus status, String jobId) {
        return ConversationTask.newTask(agentId, conversationId,
                new TaskDefinition(instruction, intentKey(instruction), instruction),
                new TaskSchedule(ScheduleType.RECURRING, Instant.now(), "P1D", status),
                new TaskRunInfo(jobId, null, null));
    }

    /**
     * Mirrors TaskService's private intentKey algorithm exactly, so test fixtures produce the same dedup key the
     * service will compute for a given instruction.
     */
    private static String intentKey(String instruction) {
        String normalized = instruction.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
