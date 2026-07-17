package io.workflowai.application;

import io.workflowai.domain.exceptions.ConversationNotFoundException;
import io.workflowai.domain.model.Conversation;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.ports.inbound.ConversationPort;
import io.workflowai.ports.outbound.ConversationStoragePort;
import io.workflowai.ports.outbound.MessageStoragePort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ConversationService implements ConversationPort {

    private final ConversationStoragePort conversationStoragePort;
    private final MessageStoragePort messageStoragePort;

    public ConversationService(ConversationStoragePort conversationStoragePort, MessageStoragePort messageStoragePort) {
        this.conversationStoragePort = conversationStoragePort;
        this.messageStoragePort = messageStoragePort;
    }

    @Override
    public Conversation createConversation(UUID agentId, String firstMessage) {
        String title = firstMessage.length() > 60 ? firstMessage.substring(0, 60) + "..." : firstMessage;
        return conversationStoragePort.create(agentId, title);
    }


    @Override
    public Conversation getConversation(UUID agent, UUID id) {
        return conversationStoragePort.findByAgentAndId(agent, id).orElseThrow(() -> new ConversationNotFoundException(agent, id));
    }


    @Override
    public List<Conversation> getConversationsForAgent(UUID agent) {
        return conversationStoragePort.findByAgent(agent);
    }


    @Override
    public List<ConversationMessage> getMessages(UUID agentId, UUID conversationId) {
        return messageStoragePort.findByAgentIdAndConversationId(agentId, conversationId);
    }


    @Override
    public void deleteConversation(UUID agentId, UUID id) {
        // TODO handle not found conversation
        conversationStoragePort.delete(agentId, id);
    }
}
