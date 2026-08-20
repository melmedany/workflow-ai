package io.workflowai.adapter.out.persistence.task;

import io.workflowai.domain.task.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

import static io.workflowai.domain.task.ConversationTask.TaskDefinition;
import static io.workflowai.domain.task.ConversationTask.TaskSchedule;

@Entity
@Table(name = "conversation_tasks")
public class ConversationTaskEntity {

    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private TaskDefinition definition;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private TaskSchedule schedule;

    @Column(name = "job_id")
    private String jobId;

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

    public ConversationTaskEntity(UUID id, UUID agentId, UUID conversationId, TaskDefinition definition,
                                  TaskSchedule schedule, String jobId) {
        this.id = id;
        this.agentId = agentId;
        this.conversationId = conversationId;
        this.definition = definition;
        this.schedule = schedule;
        this.jobId = jobId;
    }

    public void update(String newInstruction, Instant newStartDateTime, String newDuration, String newJobId) {
        this.definition = new TaskDefinition(definition.name(), definition.intentKey(), newInstruction);
        this.schedule = new TaskSchedule(schedule.type(), newStartDateTime, newDuration, schedule.status());
        this.jobId = newJobId;
    }

    public void updateStatus(TaskStatus newStatus) {
        this.schedule = new TaskSchedule(schedule.type(), schedule.startDateTime(), schedule.duration(), newStatus);
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

    public TaskDefinition definition() {
        return definition;
    }

    public TaskSchedule schedule() {
        return schedule;
    }

    public String jobId() {
        return jobId;
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