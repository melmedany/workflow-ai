package io.workflowai.domain.exceptions;

import java.util.UUID;

public class AgentNotFoundException extends RuntimeException {

    public AgentNotFoundException(UUID agentId) {
        super("Agent not found: %s".formatted(agentId));
    }
}
