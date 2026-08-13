package io.workflowai.application.port.in;

import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface TaskUseCase {

    ConversationTask createOrUpdate(UUID agentId, UUID conversationId, String instruction, ScheduleType scheduleType, Duration duration);

    void pause(UUID agentId, UUID conversationId, UUID taskId);

    void resume(UUID agentId, UUID conversationId, UUID taskId);

    void cancel(UUID agentId, UUID conversationId, UUID taskId);

    void cancelAll(UUID agentId, UUID conversationId);

    List<ConversationTask> listByConversation(UUID agentId, UUID conversationId);
}