package io.workflowai.application.port.out;

import io.workflowai.domain.task.ConversationTask;

/**
 * Scheduling capability behind a port so {@code TaskService} stays decoupled from whichever
 * recurring-job engine implements it (JobRunr today).
 */
public interface TaskScheduler {

    String schedule(ConversationTask task);

    String reschedule(ConversationTask task);

    void pause(ConversationTask task);

    void resume(ConversationTask task);

    void cancel(ConversationTask task);
}