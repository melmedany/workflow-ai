package io.workflowai.adapter.out.scheduling;

import io.workflowai.application.port.out.TaskScheduler;
import io.workflowai.domain.task.ConversationTask;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class TaskSchedulerImpl implements TaskScheduler {

    private final JobScheduler jobScheduler;

    public TaskSchedulerImpl(JobScheduler jobScheduler) {
        this.jobScheduler = jobScheduler;
    }

    @Override
    public String schedule(ConversationTask task) {
        Instant scheduleAt = task.createdAt() != null ? task.createdAt().plus(task.schedule().duration()) :
                Instant.now().plus(task.schedule().duration());

        if (task.runOnce()) {
            return jobScheduler.<ScheduledAgentTaskRunner>schedule(
                    task.id(),
                    scheduleAt,
                    runner -> runner.run(task.agentId(), task.conversationId(), task.id()))
                    .asUUID().toString();
        }

        return jobScheduler.<ScheduledAgentTaskRunner>scheduleRecurrently(
                task.id().toString(),
                task.schedule().duration(),
                runner -> runner.run(task.agentId(), task.conversationId(), task.id()));
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
}