package io.workflowai.application.task;

import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.application.port.out.TaskScheduler;
import io.workflowai.domain.exceptions.TaskNotFoundException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskDefinition;
import io.workflowai.domain.task.TaskStatus;

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

public class TaskService implements TaskUseCase {

    private final ConversationTaskStorage storage;
    private final TaskScheduler scheduler;

    public TaskService(ConversationTaskStorage storage, TaskScheduler scheduler) {
        this.storage = storage;
        this.scheduler = scheduler;
    }

    @Override
    public ConversationTask createOrUpdate(UUID agentId, UUID conversationId, String instruction,
                                           String cronExpression, Instant runOnceAt) {
        List<ConversationTask> tasks = storage.findByConversation(agentId, conversationId);
        if (tasks.isEmpty()) {
            return create(agentId, conversationId, instruction, cronExpression, runOnceAt);
        }

        String intentKey = intentKey(instruction);

        ConversationTask matchingTask = tasks.stream().filter(t -> intentKey.equalsIgnoreCase(t.definition().intentKey()))
                .findFirst()
                .orElse(null);

        if (matchingTask == null) {
            return create(agentId, conversationId, instruction, cronExpression, runOnceAt);
        } else {
            return update(matchingTask, instruction, cronExpression, runOnceAt);
        }
    }

    private ConversationTask create(UUID agentId, UUID conversationId, String instruction,
                                           String cronExpression, Instant runOnceAt) {
        String intentKey = intentKey(instruction);

        TaskDefinition definition = new TaskDefinition(instruction, intentKey, instruction); // using instruction as an initial name
        TaskSchedule schedule = new TaskSchedule(cronExpression, runOnceAt, TaskStatus.ACTIVE);
        TaskRunInfo runInfo = new TaskRunInfo(null, null);

        ConversationTask task = storage.create(ConversationTask.newTask(agentId, conversationId, definition, schedule, runInfo));
        scheduler.schedule(task);

        return task;
    }


    private ConversationTask update(ConversationTask existingTask, String instruction, String cronExpression, Instant runOnceAt) {
        ConversationTask updatedTask = storage.update(existingTask.update(instruction, cronExpression, runOnceAt));
        scheduler.reschedule(updatedTask);

        return updatedTask;
    }

    @Override
    public void pause(UUID taskId) {
        storage.updateStatus(taskId, TaskStatus.PAUSED);
        scheduler.pause(taskId);
    }

    @Override
    public void resume(UUID taskId) {
        ConversationTask task = storage.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        storage.updateStatus(taskId, TaskStatus.ACTIVE);
        scheduler.resume(task.withStatus(TaskStatus.ACTIVE));
    }

    @Override
    public void cancel(UUID taskId) {
        storage.updateStatus(taskId, TaskStatus.CANCELLED);
        scheduler.cancel(taskId);
    }

    @Override
    public List<ConversationTask> listByConversation(UUID agentId, UUID conversationId) {
        return storage.findByConversation(agentId, conversationId);
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