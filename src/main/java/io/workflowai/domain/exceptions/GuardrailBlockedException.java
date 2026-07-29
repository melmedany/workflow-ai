package io.workflowai.domain.exceptions;

import io.workflowai.domain.agent.ChatProviderId;

public class GuardrailBlockedException extends ChatProviderException {

    public GuardrailBlockedException(ChatProviderId providerId, String message) {
        super(providerId, message);
    }
}