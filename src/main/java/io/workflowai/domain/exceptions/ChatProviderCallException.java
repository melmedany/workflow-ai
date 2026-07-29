package io.workflowai.domain.exceptions;

import io.workflowai.domain.agent.ChatProviderId;

public class ChatProviderCallException extends ChatProviderException {

    public ChatProviderCallException(ChatProviderId providerId, String message) {
        super(providerId, message);
    }

    public ChatProviderCallException(ChatProviderId providerId, String message, Throwable cause) {
        super(providerId, message, cause);
    }
}