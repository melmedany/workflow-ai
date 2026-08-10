package io.workflowai.adapter.out.persistence.conversation.message;

import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.domain.conversation.ConversationMessageRole;
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
@Table(name = "messages")
public class MessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "add_to_memory", nullable = false)
    private Boolean addToMemory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageEntity() {
    }

    public MessageEntity(UUID conversationId, UUID agentId, ConversationMessage message) {
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.role = message.role();
        this.content = message.content();
        this.addToMemory = message.addToMemory();
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

    public ConversationMessageRole role() {
        return role;
    }

    public String content() {
        return content;
    }

    public boolean addToMemory() {
        return addToMemory;
    }

    public Instant createdAt() {
        return createdAt;
    }
}