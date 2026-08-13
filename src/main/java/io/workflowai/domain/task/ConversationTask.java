package io.workflowai.domain.task;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType.ONCE;

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

    public ConversationTask update(String instruction, Duration duration) {
        return new ConversationTask(id, agentId, conversationId,
                new TaskDefinition(definition.name, definition.intentKey, instruction),
                new TaskSchedule(schedule.type, duration, schedule.status), runInfo, createdAt, updatedAt);
    }

    public ConversationTask withStatus(TaskStatus newStatus) {
        return new ConversationTask(id, agentId, conversationId, definition,
                new TaskSchedule(schedule.type, schedule.duration, newStatus), runInfo, createdAt, updatedAt);
    }

    public ConversationTask withJobId(String jobId) {
        return new ConversationTask(id, agentId, conversationId, definition,
                schedule, new TaskRunInfo(jobId, runInfo.lastRunAt, runInfo.lastRunStatus), createdAt, updatedAt);
    }

    public boolean runOnce() {
        return schedule.type() == ONCE;
    }

    public Instant nextRunAt() {
        if (schedule.type() == ONCE) return createdAt.plus(schedule.duration());

        return runInfo.lastRunAt != null ? runInfo.lastRunAt.plus(schedule.duration()) : createdAt.plus(schedule.duration());
    }

    public record TaskDefinition(String name, String intentKey, String instruction) {
    }

    public record TaskSchedule(ScheduleType type, Duration duration, TaskStatus status) {

        public enum ScheduleType {
            ONCE, RECURRING
        }
    }

    public record TaskRunInfo(String jobId, Instant lastRunAt, String lastRunStatus) {
    }
}