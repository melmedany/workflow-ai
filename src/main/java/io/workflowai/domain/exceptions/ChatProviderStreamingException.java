package io.workflowai.domain.exceptions;

import io.workflowai.domain.agent.ChatProviderId;

public class ChatProviderStreamingException extends ChatProviderException {

    public ChatProviderStreamingException(ChatProviderId providerId, String message) {
        this(providerId, message, null);
    }

    public ChatProviderStreamingException(ChatProviderId providerId, String message, Throwable cause) {
        super(providerId, message, cause);
    }
}