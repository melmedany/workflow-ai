package io.workflowai.domain.exceptions;

import java.util.UUID;

public class ClassificationException extends DomainException {

    private final UUID agentId;

    public ClassificationException(UUID agentId, String message) {
        this(agentId, message, null);
    }

    public ClassificationException(UUID agentId, String message, Throwable cause) {
        super("Classification failed for agent [%s]: %s".formatted(agentId, message), cause);
        this.agentId = agentId;
    }

    public UUID getAgent() {
        return agentId;
    }
}
