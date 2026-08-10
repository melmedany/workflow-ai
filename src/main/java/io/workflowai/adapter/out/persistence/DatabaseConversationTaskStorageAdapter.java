package io.workflowai.adapter.out.persistence;

import io.workflowai.adapter.out.persistence.task.ConversationTaskEntity;
import io.workflowai.adapter.out.persistence.task.ConversationTaskRepository;
import io.workflowai.application.port.out.AgentRunTracker;
import io.workflowai.application.port.out.AgentRunTracker.AgentRunSummary;
import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.domain.exceptions.TaskNotFoundException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.workflowai.domain.task.ConversationTask.TaskDefinition;
import static io.workflowai.domain.task.ConversationTask.TaskRunInfo;
import static io.workflowai.domain.task.ConversationTask.TaskSchedule;

@Service
public class DatabaseConversationTaskStorageAdapter implements ConversationTaskStorage {

    private final ConversationTaskRepository taskRepository;
    private final AgentRunTracker agentRunTracker;

    public DatabaseConversationTaskStorageAdapter(ConversationTaskRepository taskRepository,
                                                  AgentRunTracker agentRunTracker) {
        this.taskRepository = taskRepository;
        this.agentRunTracker = agentRunTracker;
    }

    @Override
    @Transactional
    public ConversationTask create(ConversationTask task) {
        ConversationTaskEntity entity = new ConversationTaskEntity(task.agentId(), task.conversationId(), task.definition().name(),
                task.definition().intentKey(), task.definition().instruction(), task.schedule().cronExpression(), task.schedule().runOnceAt());
        return toDomain(taskRepository.save(entity));
    }

    @Override
    @Transactional
    public ConversationTask update(ConversationTask task) {
        ConversationTaskEntity existing = taskRepository.findByIntentKey(task.definition().intentKey())
                .orElseThrow(() -> new TaskNotFoundException(task.id()));
        existing.update(task.definition().instruction(), task.schedule().cronExpression(), task.schedule().runOnceAt());
        return toDomain(taskRepository.save(existing));
    }

    @Override
    public Optional<ConversationTask> findById(UUID taskId) {
        return taskRepository.findById(taskId).map(this::toDomain);
    }

    @Override
    public List<ConversationTask> findByConversation(UUID agentId, UUID conversationId) {
        return taskRepository.findByAgentIdAndConversationIdOrderByCreatedAtDesc(agentId, conversationId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void updateStatus(UUID taskId, TaskStatus status) {
        ConversationTaskEntity entity = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        entity.updateStatus(status);
        taskRepository.save(entity);
    }

    @Override
    @Transactional
    public void updateAfterRun(UUID taskId, UUID lastRunId) {
        ConversationTaskEntity entity = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));
        entity.recordRun(lastRunId);
        taskRepository.save(entity);
    }

    private ConversationTask toDomain(ConversationTaskEntity task) {
        TaskDefinition definition = new TaskDefinition(task.name(), task.intentKey(), task.instruction());
        TaskSchedule schedule = new TaskSchedule(task.cronExpression(), task.runOnceAt(), task.status());

        AgentRunSummary lastRun = task.lastRunId() != null ?
                agentRunTracker.find(task.lastRunId()).orElse(null)
                : null;

        TaskRunInfo runInfo = new TaskRunInfo(
                lastRun != null && lastRun.completedAt() != null ? lastRun.completedAt() : null,
                lastRun != null && lastRun.status() != null ? lastRun.status() : null);

        return new ConversationTask(task.id(), task.agentId(), task.conversationId(),
                definition, schedule, runInfo, task.createdAt(), task.updatedAt());
    }
}