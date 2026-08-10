package io.workflowai.adapter.out.persistence.agent.run;

import io.workflowai.domain.agent.TriggerSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_runs")
public class AgentRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false)
    private TriggerSource triggerSource;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "started_at", nullable = false)
    @CreationTimestamp
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentRunStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected AgentRunEntity() {
    }

    public AgentRunEntity(TriggerSource triggerSource, UUID agentId, UUID conversationId, UUID taskId) {
        this.triggerSource = triggerSource;
        this.agentId = agentId;
        this.conversationId = conversationId;
        this.taskId = taskId;
        this.status = AgentRunStatus.RUNNING;
    }

    public void complete() {
        this.completedAt = Instant.now();
        this.status = AgentRunStatus.COMPLETED;
        this.errorMessage = null;
    }

    public void fail(String errorMessage) {
        this.completedAt = Instant.now();
        this.status = AgentRunStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public UUID id() {
        return this.id;
    }

    public UUID agentId() {
        return this.agentId;
    }

    public UUID conversationId() {
        return this.conversationId;
    }

    public TriggerSource triggerSource() {
        return this.triggerSource;
    }

    public UUID taskId() {
        return this.taskId;
    }

    public AgentRunStatus status() {
        return this.status;
    }

    public String errorMessage() {
        return this.errorMessage;
    }

    public Instant startedAt() {
        return this.startedAt;
    }

    public Instant completedAt() {
        return this.completedAt;
    }
}
