package io.workflowai.adapter.out.persistence.task;

import io.workflowai.domain.task.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversation_tasks")
public class ConversationTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column
    private String name;

    @Column(name = "intent_key", nullable = false)
    private String intentKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String instruction;

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "run_once_at", nullable = false)
    private Instant runOnceAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(name = "last_run_id")
    private UUID lastRunId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationTaskEntity() {
    }

    public ConversationTaskEntity(UUID agentId, UUID conversationId, String name, String intentKey,
                                  String instruction, String cronExpression, Instant runOnceAt) {
        this.agentId = agentId;
        this.conversationId = conversationId;
        this.name = name;
        this.intentKey = intentKey;
        this.instruction = instruction;
        this.cronExpression = cronExpression;
        this.runOnceAt = runOnceAt;
        this.status = TaskStatus.ACTIVE;
    }

    public void update(String instruction, String cronExpression, Instant runOnceAt) {
        this.instruction = instruction;
        this.cronExpression = cronExpression;
        this.runOnceAt = runOnceAt;
    }

    public void updateStatus(TaskStatus status) {
        this.status = status;
    }

    public void recordRun(UUID lastRunId) {
        this.lastRunId = lastRunId;
    }

    public UUID id() {
        return id;
    }

    public UUID agentId() {
        return agentId;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public String name() {
        return name;
    }

    public String intentKey() {
        return intentKey;
    }

    public String instruction() {
        return instruction;
    }

    public String cronExpression() {
        return cronExpression;
    }

    public Instant runOnceAt() {
        return runOnceAt;
    }

    public TaskStatus status() {
        return status;
    }

    public UUID lastRunId() {
        return lastRunId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}