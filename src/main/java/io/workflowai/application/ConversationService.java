package io.workflowai.application;

import io.workflowai.domain.exceptions.ConversationNotFoundException;
import io.workflowai.domain.model.Conversation;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.ports.inbound.ConversationProvider;
import io.workflowai.ports.outbound.ConversationStorage;
import io.workflowai.ports.outbound.MessageStorage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService implements ConversationProvider {

    private final ConversationStorage conversationStorage;
    private final MessageStorage messageStorage;

    public ConversationService(ConversationStorage conversationStorage, MessageStorage messageStorage) {
        this.conversationStorage = conversationStorage;
        this.messageStorage = messageStorage;
    }

    @Override
    public Conversation createConversation(UUID agentId, String firstMessage) {
        String title = firstMessage.length() > 60 ? firstMessage.substring(0, 60) + "..." : firstMessage;
        return conversationStorage.create(agentId, title);
    }


    @Override
    public Conversation getConversation(UUID agent, UUID id) {
        return conversationStorage.findByAgentAndId(agent, id).orElseThrow(() -> new ConversationNotFoundException(agent, id));
    }


    @Override
    public List<Conversation> getConversationsForAgent(UUID agent) {
        return conversationStorage.findByAgent(agent);
    }


    @Override
    public List<ConversationMessage> getMessages(UUID agentId, UUID conversationId) {
        return messageStorage.findByAgentIdAndConversationId(agentId, conversationId);
    }


    @Override
    public void deleteConversation(UUID agentId, UUID id) {
        conversationStorage.delete(agentId, id);
    }
}
