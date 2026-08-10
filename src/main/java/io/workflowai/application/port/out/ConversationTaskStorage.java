package io.workflowai.application.port.out;

import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.TaskStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationTaskStorage {

    ConversationTask create(ConversationTask candidate);

    ConversationTask update(ConversationTask candidate);

    Optional<ConversationTask> findById(UUID taskId);

    List<ConversationTask> findByConversation(UUID agentId, UUID conversationId);

    void updateStatus(UUID taskId, TaskStatus status);

    void updateAfterRun(UUID taskId, UUID lastRunId);
}