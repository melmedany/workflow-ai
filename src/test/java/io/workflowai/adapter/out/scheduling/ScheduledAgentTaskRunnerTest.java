package io.workflowai.adapter.out.scheduling;

import io.workflowai.application.execution.AgentRequest;
import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.domain.agent.TriggerSource;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskDefinition;
import io.workflowai.domain.task.ConversationTask.TaskRunInfo;
import io.workflowai.domain.task.ConversationTask.TaskSchedule;
import io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType;
import io.workflowai.domain.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduledAgentTaskRunnerTest {

    private final ConversationTaskStorage storage = mock();
    private final AgentUseCase agentUseCase = mock();

    private final ScheduledAgentTaskRunner runner = new ScheduledAgentTaskRunner(storage, agentUseCase);

    private final UUID agentId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID taskId = UUID.randomUUID();

    @Test
    void skipsWhenTaskIsNotFound() {
        when(storage.findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE)).thenReturn(Optional.empty());

        runner.run(agentId, conversationId, taskId);

        verifyNoInteractions(agentUseCase);
        verify(storage, never()).updateAfterRun(any(), any(), any(), any());
        verify(storage, never()).updateStatus(any(), any(), any(), any());
    }

    @Test
    void skipsWhenTaskIsPaused() {
        ConversationTask task = task(ScheduleType.RECURRING, TaskStatus.PAUSED);
        when(storage.findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE)).thenReturn(Optional.of(task));

        runner.run(agentId, conversationId, taskId);

        verifyNoInteractions(agentUseCase);
        verify(storage, never()).updateAfterRun(any(), any(), any(), any());
    }

    @Test
    void skipsWhenTaskIsCancelled() {
        ConversationTask task = task(ScheduleType.RECURRING, TaskStatus.CANCELLED);
        when(storage.findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE)).thenReturn(Optional.of(task));

        runner.run(agentId, conversationId, taskId);

        verifyNoInteractions(agentUseCase);
    }

    @Test
    void triggersTheAgentWithASystemTriggerRequestForAnActiveTask() {
        ConversationTask task = task(ScheduleType.RECURRING, TaskStatus.ACTIVE);
        when(storage.findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE)).thenReturn(Optional.of(task));
        UUID runId = UUID.randomUUID();
        when(agentUseCase.trigger(any(AgentRequest.class), any())).thenReturn(runId);

        runner.run(agentId, conversationId, taskId);

        verify(agentUseCase).trigger(argThat(request ->
                request.triggerSource() == TriggerSource.SYSTEM_TRIGGER
                        && request.agentId().equals(agentId)
                        && request.conversationId().equals(conversationId)
                        && request.taskId().equals(taskId)
                        && request.message().equals("SCHEDULED: Summarize open PRs")),
                any());
    }

    @Test
    void onceTaskIsMarkedCompletedAfterRunning() {
        ConversationTask task = task(ScheduleType.ONCE, TaskStatus.ACTIVE);
        when(storage.findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE)).thenReturn(Optional.of(task));
        UUID runId = UUID.randomUUID();
        when(agentUseCase.trigger(any(AgentRequest.class), any())).thenReturn(runId);

        runner.run(agentId, conversationId, taskId);

        verify(storage).updateAfterRun(agentId, conversationId, taskId, runId);
        verify(storage).updateStatus(agentId, conversationId, taskId, TaskStatus.COMPLETED);
    }

    @Test
    void recurringTaskIsNotMarkedCompletedAfterRunning() {
        ConversationTask task = task(ScheduleType.RECURRING, TaskStatus.ACTIVE);
        when(storage.findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE)).thenReturn(Optional.of(task));
        UUID runId = UUID.randomUUID();
        when(agentUseCase.trigger(any(AgentRequest.class), any())).thenReturn(runId);

        runner.run(agentId, conversationId, taskId);

        verify(storage).updateAfterRun(agentId, conversationId, taskId, runId);
        verify(storage, never()).updateStatus(any(), any(), any(), any());
    }

    private ConversationTask task(ScheduleType scheduleType, TaskStatus status) {
        return new ConversationTask(taskId, agentId, conversationId,
                new TaskDefinition("Summarize open PRs", "intent-key", "Summarize open PRs"),
                new TaskSchedule(scheduleType, Instant.now(), "P1D", status),
                new TaskRunInfo("job-1", null, null),
                Instant.now(), Instant.now());
    }
}
