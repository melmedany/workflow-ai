package io.workflowai.adapter.out.persistence.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    private String details;

    @Column(name = "chat_properties", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String chatProperties;

    @Column(name = "workflow_policy_properties", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String workflowPolicyProperties;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentEntity() {
    }

    public AgentEntity(String details, String chatProperties, String workflowPolicyProperties) {
        this.details = details;
        this.chatProperties = chatProperties;
        this.workflowPolicyProperties = workflowPolicyProperties;
    }

    public UUID id() {
        return id;
    }

    public String details() {
        return details;
    }

    public String chatProperties() {
        return chatProperties;
    }

    public String workflowPolicyProperties() {
        return workflowPolicyProperties;
    }

    public void update(String details, String chatProperties, String workflowPolicyProperties) {
        this.details = details;
        this.chatProperties = chatProperties;
        this.workflowPolicyProperties = workflowPolicyProperties;
    }
}