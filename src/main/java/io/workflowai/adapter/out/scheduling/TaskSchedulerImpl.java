package io.workflowai.adapter.out.scheduling;

import io.workflowai.application.port.out.TaskScheduler;
import io.workflowai.domain.exceptions.ScheduleTooFrequentException;
import io.workflowai.domain.task.ConversationTask;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.UUID;

@Service
public class TaskSchedulerImpl implements TaskScheduler {

    private final static Duration MINIMUM_DURATION = Duration.parse("PT1M");

    private final JobScheduler jobScheduler;

    public TaskSchedulerImpl(JobScheduler jobScheduler) {
        this.jobScheduler = jobScheduler;
    }

    @Override
    public String schedule(ConversationTask task) {
        if (isTooFrequent(task.schedule().parsedDuration())) {
            throw new ScheduleTooFrequentException("Schedule is too frequent: %s".formatted(task.schedule().duration()));
        }

        if (task.runOnce()) {
            return scheduleOnce(task);
        }

        return scheduleRecurrently(task);
    }

    @Override
    public String reschedule(ConversationTask task) {
        return schedule(task);
    }

    @Override
    public void pause(ConversationTask task) {
        cancel(task);
    }

    @Override
    public void resume(ConversationTask task) {
        schedule(task);
    }

    @Override
    public void cancel(ConversationTask task) {
        if (task.runOnce()) {
            jobScheduler.delete(UUID.fromString(task.runInfo().jobId()));
        } else {
            jobScheduler.deleteRecurringJob(task.runInfo().jobId());
        }
    }

    private String scheduleOnce(ConversationTask task) {
        Instant scheduleAt = task.schedule().startDateTime() != null
                ? task.schedule().plusDuration(task.schedule().startDateTime())
                : task.schedule().plusDuration(Instant.now());

        return jobScheduler.<ScheduledAgentTaskRunner>schedule(
                        task.id(), scheduleAt,
                        runner -> runner.run(task.agentId(), task.conversationId(), task.id()))
                .asUUID().toString();
    }

    private String scheduleRecurrently(ConversationTask task) {
        Instant scheduleAt = task.schedule().startDateTime() != null ?
                task.schedule().plusDuration(task.schedule().startDateTime()) : Instant.now();

        String cronExpression;
        if (task.schedule().parsedDuration() instanceof Period p) {
            cronExpression = toCron(scheduleAt, p);
        } else if (task.schedule().parsedDuration() instanceof Duration d) {
            cronExpression = toCron(scheduleAt, d);
        } else {
            throw new IllegalArgumentException("Unknown temporal type");
        }

        return jobScheduler.<ScheduledAgentTaskRunner>scheduleRecurrently(
                task.id().toString(), cronExpression,
                runner -> runner.run(task.agentId(), task.conversationId(), task.id()));
    }

    private String toCron(Instant startDateTime, Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Duration must be positive: %s".formatted(duration));
        }

        long minutes = duration.toMinutes();
        if (minutes <= 0) {
            throw new IllegalArgumentException("Duration must be at least one minute: %s".formatted(duration));
        }

        ZonedDateTime start = startDateTime.atZone(ZoneId.systemDefault());
        int minute = start.getMinute();
        int hour = start.getHour();

        if (minutes < 60) {
            if (60 % minutes != 0) {
                throw new IllegalArgumentException(
                        "Sub-hour duration must evenly divide 60 minutes to anchor to the start time: %s".formatted(duration));
            }

            StringBuilder minutesOfHour = new StringBuilder();
            for (long m = minute % minutes; m < 60; m += minutes) {
                if (!minutesOfHour.isEmpty()) {
                    minutesOfHour.append(',');
                }
                minutesOfHour.append(m);
            }

            return "%s * * * *".formatted(minutesOfHour);
        }

        if (minutes % 60 != 0) {
            throw new IllegalArgumentException("Duration must be a whole number of hours: %s".formatted(duration));
        }

        long hours = minutes / 60;

        if (hours < 24) {
            return "%d %d-23/%d * * *".formatted(minute, hour, hours);
        }

        if (hours == 24) {
            return "%d %d * * *".formatted(minute, hour);
        }

        throw new IllegalArgumentException("Unsupported clock duration interval: %s".formatted(duration));
    }

    private String toCron(Instant startDateTime, Period period) {
        ZonedDateTime start = startDateTime.atZone(ZoneId.systemDefault());

        int minute = start.getMinute();
        int hour = start.getHour();
        int dayOfMonth = start.getDayOfMonth();
        int dayOfWeek = start.getDayOfWeek().getValue();

        int years = period.getYears();
        int months = period.getMonths();
        int days = period.getDays();

        if (years != 0) {
            throw new IllegalArgumentException("Unsupported calendar period interval: %s".formatted(period));
        }

        if (months > 0) {
            if (days != 0) {
                throw new IllegalArgumentException("Unsupported calendar period interval: %s".formatted(period));
            }

            return "%d %d %d */%d *".formatted(minute, hour, dayOfMonth, months);
        }

        if (days <= 0) {
            throw new IllegalArgumentException("Unsupported calendar period interval: %s".formatted(period));
        }

        return switch (days) {
            case 1 -> "%d %d * * *".formatted(minute, hour);
            case 7 -> "%d %d * * %d".formatted(minute, hour, dayOfWeek);
            default -> throw new IllegalArgumentException("Unsupported calendar period interval: %s".formatted(period));
        };
    }

    private boolean isTooFrequent(TemporalAmount temporal) {
        try {
            if (temporal instanceof Duration duration) {
                return duration.compareTo(MINIMUM_DURATION) < 0;
            }
            return false; // any period is not too frequent
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}