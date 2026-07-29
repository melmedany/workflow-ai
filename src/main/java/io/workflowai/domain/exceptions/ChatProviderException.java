package io.workflowai.domain.exceptions;

import io.workflowai.domain.agent.ChatProviderId;

public class ChatProviderException extends DomainException {

    public ChatProviderException(ChatProviderId providerId, String message) {
        this(providerId, message, null);
    }

    public ChatProviderException(ChatProviderId providerId, String message, Throwable cause) {
        super("Provider [%s]: %s".formatted(providerId, message), cause);
    }
}