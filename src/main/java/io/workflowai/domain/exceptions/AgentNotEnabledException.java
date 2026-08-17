package io.workflowai.domain.exceptions;

import java.util.UUID;

public class AgentNotEnabledException extends DomainException {

    public AgentNotEnabledException(UUID agentId) {
        super("Agent %s is not enabled, therefore it cannot execute any workflow".formatted(agentId));
    }
}
