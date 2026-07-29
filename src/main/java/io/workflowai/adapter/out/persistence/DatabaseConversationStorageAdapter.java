package io.workflowai.adapter.out.persistence;

import io.workflowai.adapter.out.persistence.conversation.ConversationEntity;
import io.workflowai.adapter.out.persistence.conversation.ConversationRepository;
import io.workflowai.domain.conversation.Conversation;
import io.workflowai.application.port.out.ConversationStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatabaseConversationStorageAdapter implements ConversationStorage {

    private final ConversationRepository repository;

    public DatabaseConversationStorageAdapter(ConversationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Conversation create(UUID agentId, String title) {
        return toDomain(repository.save(new ConversationEntity(agentId, title)));
    }

    @Override
    public Optional<Conversation> findByAgentAndId(UUID agentId, UUID id) {
        return repository.findByAgentIdAndId(agentId, id).map(this::toDomain);
    }

    @Override
    public List<Conversation> findByAgent(UUID agentId) {
        return repository.findByAgentIdOrderByCreatedAtDesc(agentId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID agentId, UUID conversationId) {
        repository.deleteByAgentIdAndId(agentId, conversationId);
    }

    private Conversation toDomain(ConversationEntity e) {
        return new Conversation(e.id(), e.agentId(), e.title(), e.createdAt(), e.updatedAt());
    }
}