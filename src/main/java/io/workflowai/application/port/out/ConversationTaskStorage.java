package io.workflowai.application.port.out;

import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationTaskStorage {

    ConversationTask create(ConversationTask task);

    ConversationTask update(ConversationTask task);

    Optional<ConversationTask> findTaskWithStatus(UUID agentId, UUID conversationId, UUID taskId, TaskStatus status);

    Optional<ConversationTask> findTask(UUID agentId, UUID conversationId, UUID taskId);

    List<ConversationTask> findByConversation(UUID agentId, UUID conversationId);

    void updateStatus(UUID agentId, UUID conversationId, UUID taskId, TaskStatus status);

    void updateAfterRun(UUID agentId, UUID conversationId, UUID taskId, UUID lastRunId);
}