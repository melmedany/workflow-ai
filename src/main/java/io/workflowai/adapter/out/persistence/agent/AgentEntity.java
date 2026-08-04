package io.workflowai.adapter.out.persistence.agent;

import io.workflowai.domain.agent.AgentDetails;
import io.workflowai.domain.agent.ChatProperties;
import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agents")
public class AgentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private AgentDetails details;

    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_id", nullable = false, length = 30)
    private WorkflowId workflowId;

    @Column(name = "chat_properties", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private ChatProperties chatProperties;

    @Column(name = "workflow_policy", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private WorkflowPolicy workflowPolicy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentEntity() {
    }

    public AgentEntity(AgentDetails details, WorkflowId workflowId, ChatProperties chatProperties, WorkflowPolicy workflowPolicy) {
        this.details = details;
        this.workflowId = workflowId;
        this.chatProperties = chatProperties;
        this.workflowPolicy = workflowPolicy;
    }

    public UUID id() {
        return id;
    }

    public AgentDetails details() {
        return details;
    }

    public WorkflowId workflowId() {
        return workflowId;
    }

    public ChatProperties chatProperties() {
        return chatProperties;
    }

    public WorkflowPolicy workflowPolicy() {
        return workflowPolicy;
    }

    public void update(AgentDetails details, WorkflowId workflowId, ChatProperties chatProperties, WorkflowPolicy workflowPolicy) {
        this.details = details;
        this.workflowId = workflowId;
        this.chatProperties = chatProperties;
        this.workflowPolicy = workflowPolicy;
    }
}