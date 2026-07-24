package io.workflowai.adapters.outbound.persistence.agentmemory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_memory")
public class AgentMemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "agent_id", nullable = false, length = 100)
    private UUID agentId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentMemoryEntity() {
    }

    public AgentMemoryEntity(UUID conversationId, UUID agentId, String content) {
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.content = content;
    }

    public UUID id() {
        return id;
    }

    public UUID conversationId() {
        return conversationId;
    }

    public UUID agentId() {
        return agentId;
    }

    public String content() {
        return content;
    }

    public void replaceContent(String content) {
        this.content = content;
    }

    public Instant createdAt() {
        return createdAt;
    }
}