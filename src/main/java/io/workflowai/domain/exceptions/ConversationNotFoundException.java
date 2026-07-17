package io.workflowai.domain.exceptions;

import java.util.UUID;

public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(UUID agentId, UUID id) {
        super("Conversation: %s not found for agent: %s".formatted(id, agentId));
    }
}
