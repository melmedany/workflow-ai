package io.workflowai.application.port.out;

import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationTaskStorage {

    ConversationTask create(ConversationTask task);

    ConversationTask update(ConversationTask task);

    Optional<ConversationTask> findActiveTask(UUID agentId, UUID conversationId, UUID taskId);

    List<ConversationTask> findByConversation(UUID agentId, UUID conversationId);

    void updateStatus(UUID agentId, UUID conversationId, UUID taskId, TaskStatus status);

    void updateJobId(UUID agentId, UUID conversationId, UUID taskId, String jobId);

    void updateAfterRun(UUID agentId, UUID conversationId, UUID taskId, UUID lastRunId);
}