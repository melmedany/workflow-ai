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
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

        String intentKey = generateIntentKey(instruction);

        ConversationTask matchingTask = tasks.stream().filter(t -> intentKey.equals(t.definition().intentKey()))
                .findFirst()
                .orElse(null);

        if (matchingTask == null) {
            return create(agentId, conversationId, instruction, scheduleType, startDateTime, duration);
        }

        return update(matchingTask, instruction, startDateTime, duration);
    }

    private ConversationTask create(UUID agentId, UUID conversationId, String instruction,
                                    ScheduleType scheduleType, Instant startDateTime, String duration) {
        String intentKey = generateIntentKey(instruction);

        TaskDefinition definition = new TaskDefinition(instruction, intentKey, instruction); // using instruction as an initial task name
        TaskSchedule schedule = new TaskSchedule(scheduleType, startDateTime, duration, TaskStatus.ACTIVE);
        TaskRunInfo runInfo = new TaskRunInfo(null, null, null);

        ConversationTask task = ConversationTask.newTask(agentId, conversationId, definition, schedule, runInfo);

        String jobId = scheduler.schedule(task);
        ConversationTask scheduledTask = task.withJobId(jobId);
        try {
            return storage.create(scheduledTask);
        } catch (RuntimeException ex) {
            log.error("Failed to create conversation task: {}", task.id(), ex);
            scheduler.cancel(scheduledTask);
            throw ex;
        }
    }

    private ConversationTask update(ConversationTask existingTask, String instruction, Instant startDateTime, String duration) {
        ConversationTask updatedTask = existingTask.update(instruction, startDateTime, duration);

        boolean reschedule = existingTask.schedule().status() == TaskStatus.ACTIVE;
        if (reschedule) {
            updatedTask = updatedTask.withJobId(scheduler.reschedule(updatedTask));
        }

        try {
            return storage.update(updatedTask);
        } catch (RuntimeException ex) {
            log.error("Failed to update conversation task: {}, keeping original job scheduled", existingTask.id(), ex);
            if (reschedule) {
                scheduler.reschedule(existingTask);
            }
            throw ex;
        }
    }

    @Override
    public void pause(UUID agentId, UUID conversationId, UUID taskId) {
        ConversationTask task = findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE);
        scheduler.pause(task);
        try {
            storage.updateStatus(agentId, conversationId, taskId, TaskStatus.PAUSED);
        } catch (RuntimeException ex) {
            log.error("Failed to pause conversation task: {}, keeping original job running", task.id(), ex);
            scheduler.resume(task);
            throw ex;
        }
    }

    @Override
    public void resume(UUID agentId, UUID conversationId, UUID taskId) {
        ConversationTask task = findTaskWithStatus(agentId, conversationId, taskId, TaskStatus.PAUSED);
        ConversationTask activeTask = task.withStatus(TaskStatus.ACTIVE);
        scheduler.resume(activeTask);
        try {
            storage.updateStatus(agentId, conversationId, taskId, TaskStatus.ACTIVE);
        } catch (RuntimeException ex) {
            log.error("Failed to resume conversation task: {}, keeping original job paused", task.id(), ex);
            scheduler.pause(task);
            throw ex;
        }
    }

    @Override
    public void cancel(UUID agentId, UUID conversationId, UUID taskId) {
        ConversationTask task = storage.findTask(agentId, conversationId, taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        cancel(task);
    }

    @Override
    public void cancelAll(UUID agentId, UUID conversationId) {
        List<ConversationTask> tasks = storage.findByConversation(agentId, conversationId);

        for (ConversationTask task : tasks) {
            try {
                cancel(task);
            } catch (JobNotFoundException | IllegalJobStateChangeException ex) {
                log.warn("Failed to cancel job {}", task.id(), ex);
            } catch (TaskNotFoundException ex) {
                log.warn("Failed to cancel task {}", task.id(), ex);
            }
        }
    }

    public void cancel(ConversationTask task) {
        try {
            scheduler.cancel(task);
            storage.updateStatus(task.agentId(), task.conversationId(), task.id(), TaskStatus.CANCELLED);
            log.debug("Cancelled task {} - jobId {}", task.id(), task.runInfo().jobId());
        } catch (RuntimeException ex) {
            log.error("Failed to cancel conversation task: {}, keeping original job scheduled", task.id(), ex);
            scheduler.schedule(task);
            throw ex;
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

    private static String generateIntentKey(String instruction) {
        String normalized = instruction.trim().toLowerCase(Locale.ROOT);
        return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    }
}