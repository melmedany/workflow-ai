package io.workflowai.application.port.out;

import io.workflowai.domain.task.ConversationTask;

import java.util.UUID;

/**
 * Scheduling capability behind a port so {@code TaskService} stays decoupled from whichever
 * recurring-job engine implements it (JobRunr today).
 */
public interface TaskScheduler {

    void schedule(ConversationTask task);

    void reschedule(ConversationTask task);

    void pause(UUID taskId);

    void resume(ConversationTask task);

    void cancel(UUID taskId);
}