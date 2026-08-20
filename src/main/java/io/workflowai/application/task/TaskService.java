package io.workflowai.application.task;

import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.application.port.out.TaskScheduler;
import io.workflowai.domain.exceptions.TaskNotFoundException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskDefinition;
import io.workflowai.domain.task.TaskStatus;
import org.jobrunr.jobs.states.IllegalJobStateChangeException;
import org.jobrunr.storage.JobNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static io.workflowai.domain.task.ConversationTask.TaskRunInfo;
import static io.workflowai.domain.task.ConversationTask.TaskSchedule;
import static io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType;

public class TaskService implements TaskUseCase {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final ConversationTaskStorage storage;
    private final TaskScheduler scheduler;

    public TaskService(ConversationTaskStorage storage, TaskScheduler scheduler) {
        this.storage = storage;
        this.scheduler = scheduler;
    }


    @Override
    public ConversationTask createOrUpdate(UUID agentId, UUID conversationId, String instruction,
                                           ScheduleType scheduleType, Instant startDateTime, String duration) {
        List<ConversationTask> tasks = storage.findByConversation(agentId, conversationId);
        if (tasks.isEmpty()) {
            return create(agentId, conversationId, instruction, scheduleType, startDateTime, duration);
        }

        String intentKey = intentKey(instruction);

        ConversationTask matchingTask = tasks.stream().filter(t -> intentKey.equalsIgnoreCase(t.definition().intentKey()))
                .findFirst()
                .orElse(null);

        if (matchingTask == null) {
            return create(agentId, conversationId, instruction, scheduleType, startDateTime, duration);
        } else {
            return update(matchingTask, instruction, startDateTime, duration);
        }
    }

    private ConversationTask create(UUID agentId, UUID conversationId, String instruction,
                                    ScheduleType scheduleType, Instant startDateTime, String duration) {
        String intentKey = intentKey(instruction);

        TaskDefinition definition = new TaskDefinition(instruction, intentKey, instruction); // using instruction as an initial task name
        TaskSchedule schedule = new TaskSchedule(scheduleType, startDateTime, duration, TaskStatus.ACTIVE);
        TaskRunInfo runInfo = new TaskRunInfo(null, null, null);

        ConversationTask task = ConversationTask.newTask(agentId, conversationId, definition, schedule, runInfo);

        String jobId = scheduler.schedule(task);
        return storage.create(task.withJobId(jobId));
    }

    private ConversationTask update(ConversationTask existingTask, String instruction, Instant startDateTime, String duration) {
        ConversationTask updatedTask = existingTask.update(instruction, startDateTime, duration);

        String jobId = scheduler.reschedule(updatedTask);

        return storage.update(updatedTask.withJobId(jobId));
    }

    @Override
    public void pause(UUID agentId, UUID conversationId, UUID taskId) {
        ConversationTask task = findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE);
        scheduler.pause(task);
        storage.updateStatus(agentId, conversationId, taskId, TaskStatus.PAUSED);
    }

    @Override
    public void resume(UUID agentId, UUID conversationId, UUID taskId) {
        ConversationTask task = findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.PAUSED);
        scheduler.resume(task.withStatus(TaskStatus.ACTIVE));
        storage.updateStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE);
    }

    @Override
    public void cancel(UUID agentId, UUID conversationId, UUID taskId) {
        ConversationTask task = storage.findTask(agentId, conversationId, taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        scheduler.cancel(task);
        storage.updateStatus(agentId, conversationId, taskId, TaskStatus.CANCELLED);
    }

    @Override
    public void cancelAll(UUID agentId, UUID conversationId) {
        List<ConversationTask> tasks = storage.findByConversation(agentId, conversationId);

        for (ConversationTask task : tasks) {
            try {
                scheduler.cancel(task);
                storage.updateStatus(agentId, conversationId, task.id(), TaskStatus.CANCELLED);
                log.debug("Cancelled task {} - jobId {}", task.id(), task.runInfo().jobId());
            } catch (JobNotFoundException | IllegalJobStateChangeException ex) {
                log.warn("Failed to cancel job {}", task.id(), ex);
            } catch (TaskNotFoundException ex) {
                log.warn("Failed to cancel task {}", task.id(), ex);
            }
        }
    }

    @Override
    public List<ConversationTask> listByConversation(UUID agentId, UUID conversationId) {
        return storage.findByConversation(agentId, conversationId);
    }

    private ConversationTask findTaskWithStatus(UUID agentId, UUID conversationId, UUID taskId, TaskStatus status) {
        return storage.findTaskWithStatus(agentId, conversationId, taskId, status)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }

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