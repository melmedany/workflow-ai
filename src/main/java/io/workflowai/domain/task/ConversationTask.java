package io.workflowai.domain.task;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
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
        return new ConversationTask(UUID.randomUUID(), agentId, conversationId, definition, schedule, runInfo, null, null);
    }

    public ConversationTask update(String instruction, Instant startDateTime, String duration) {
        return new ConversationTask(id, agentId, conversationId,
                new TaskDefinition(definition.name, definition.intentKey, instruction),
                new TaskSchedule(schedule.type, startDateTime, duration, schedule.status), runInfo, createdAt, updatedAt);
    }

    public ConversationTask withStatus(TaskStatus newStatus) {
        return new ConversationTask(id, agentId, conversationId, definition,
                new TaskSchedule(schedule.type, schedule.startDateTime, schedule.duration, newStatus), runInfo, createdAt, updatedAt);
    }

    public ConversationTask withJobId(String jobId) {
        return new ConversationTask(id, agentId, conversationId, definition,
                schedule, new TaskRunInfo(jobId, runInfo.lastRunAt, runInfo.lastRunStatus), createdAt, updatedAt);
    }

    public boolean runOnce() {
        return schedule.type() == ONCE;
    }

    public Instant nextRunAt() {
        if (schedule.type() == ONCE) return schedule.plusDuration(schedule.startDateTime);

        return runInfo.lastRunAt != null ? schedule.plusDuration(runInfo.lastRunAt)
                : schedule.plusDuration(schedule.startDateTime);
    }

    public record TaskDefinition(String name, String intentKey, String instruction) {
    }

    public record TaskSchedule(ScheduleType type, Instant startDateTime, String duration, TaskStatus status) {

        public TemporalAmount parsedDuration() {
            if (duration == null || duration.isBlank()) {
                throw new IllegalArgumentException("Duration string cannot be null or empty");
            }

            try {
                if (duration.startsWith("PT")) {
                    return Duration.parse(duration);
                } else {
                    return Period.parse(duration);
                }
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Failed to parse duration string: %s".formatted(duration), e);
            }
        }

        public Instant plusDuration(Instant base) {
            TemporalAmount amount = parsedDuration();
            if (amount instanceof Period period) {
                return base.atZone(ZoneId.systemDefault()).plus(period).toInstant();
            }
            return base.plus(amount);
        }

        public enum ScheduleType {
            ONCE, RECURRING, UNDEFINED;

            public static ScheduleType fromString(String type) {
                if (type == null) return UNDEFINED;

                return switch (type.toUpperCase()) {
                    case "ONCE" -> ONCE;
                    case "RECURRING" -> RECURRING;
                    default -> UNDEFINED;
                };
            }
        }
    }

    public record TaskRunInfo(String jobId, Instant lastRunAt, String lastRunStatus) {
    }
}