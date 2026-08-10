package io.workflowai.adapter.out.scheduling;

import io.workflowai.application.port.out.TaskScheduler;
import io.workflowai.domain.task.ConversationTask;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TaskSchedulerImpl implements TaskScheduler {

    private final JobScheduler jobScheduler;

    public TaskSchedulerImpl(JobScheduler jobScheduler) {
        this.jobScheduler = jobScheduler;
    }

    @Override
    public void schedule(ConversationTask task) {
        if (task.schedule().runOnceAt() != null) {
            jobScheduler.<ScheduledAgentTaskRunner>schedule(
                    task.id(),
                    task.schedule().runOnceAt(),
                    runner -> runner.run(task.id()));
            return;
        }

        jobScheduler.<ScheduledAgentTaskRunner>scheduleRecurrently(
                task.id().toString(),
                task.schedule().cronExpression(),
                runner -> runner.run(task.id()));
    }

    @Override
    public void reschedule(ConversationTask task) {
        schedule(task);
    }

    @Override
    public void pause(UUID taskId) {
        BackgroundJob.deleteRecurringJob(taskId.toString());
    }

    @Override
    public void resume(ConversationTask task) {
        schedule(task);
    }

    @Override
    public void cancel(UUID taskId) {
        BackgroundJob.deleteRecurringJob(taskId.toString());
    }
}