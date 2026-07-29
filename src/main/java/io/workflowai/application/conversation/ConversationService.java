package io.workflowai.application.conversation;

import io.workflowai.domain.exceptions.ConversationNotFoundException;
import io.workflowai.domain.conversation.Conversation;
import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.application.port.in.ConversationProvider;
import io.workflowai.application.port.out.ConversationStorage;
import io.workflowai.application.port.out.ConversationMessageStorage;

import java.util.List;
import java.util.UUID;

public class ConversationService implements ConversationProvider {

    private final ConversationStorage conversationStorage;
    private final ConversationMessageStorage conversationMessageStorage;

    public ConversationService(ConversationStorage conversationStorage, ConversationMessageStorage conversationMessageStorage) {
        this.conversationStorage = conversationStorage;
        this.conversationMessageStorage = conversationMessageStorage;
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
        return conversationMessageStorage.findByAgentIdAndConversationId(agentId, conversationId);
    }


    @Override
    public void deleteConversation(UUID agentId, UUID id) {
        conversationStorage.delete(agentId, id);
    }
}
