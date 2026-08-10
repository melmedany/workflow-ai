package io.workflowai.application.port.in;

import io.workflowai.domain.task.ConversationTask;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskUseCase {

    ConversationTask createOrUpdate(UUID agentId, UUID conversationId, String instruction, String cronExpression, Instant runOnceAt);

    void pause(UUID taskId);

    void resume(UUID taskId);

    void cancel(UUID taskId);

    List<ConversationTask> listByConversation(UUID agentId, UUID conversationId);
}