package io.workflowai.domain.task;

import io.workflowai.domain.exceptions.InvalidScheduleException;
import org.jobrunr.scheduling.cron.CronExpression;
import org.jobrunr.scheduling.cron.InvalidCronExpressionException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public record ConversationTask(
        UUID id,
        UUID agentId,
        UUID conversationId,
        TaskDefinition definition,
        TaskSchedule schedule,
        TaskRunInfo runInfo,
        Instant createdAt,
        Instant updatedAt
) {

    public static ConversationTask newTask(UUID agentId, UUID conversationId, TaskDefinition definition,
                                           TaskSchedule schedule, TaskRunInfo runInfo) {
        return new ConversationTask(null, agentId, conversationId, definition, schedule, runInfo, null, null);
    }

    public ConversationTask update(String instruction, String cronExpression, Instant runOnceAt) {
        return new ConversationTask(id, agentId, conversationId,
                new TaskDefinition(definition.name, definition.intentKey, instruction),
                new TaskSchedule(cronExpression, runOnceAt, schedule.status), runInfo, createdAt, updatedAt);
    }

    public ConversationTask withStatus(TaskStatus newStatus) {
        return new ConversationTask(id, agentId, conversationId, definition,
                new TaskSchedule(schedule.cronExpression, schedule.runOnceAt, newStatus), runInfo, createdAt, updatedAt);
    }

    public Instant nextRunAt() {
        if (schedule.runOnceAt() != null)
            return schedule.runOnceAt();

        try {
            return new CronExpression(schedule.cronExpression).next(createdAt, Instant.now(), ZoneId.systemDefault());
        } catch (InvalidCronExpressionException | DateTimeException ex) {
            throw new InvalidScheduleException("Could not parse cron expression [%s]".formatted(schedule.cronExpression), ex);
        }
    }

    public record TaskDefinition(String name, String intentKey, String instruction) {
    }

    public record TaskSchedule(String cronExpression, Instant runOnceAt, TaskStatus status) {
    }

    public record TaskRunInfo(Instant lastRunAt, String lastRunStatus) {
    }
}