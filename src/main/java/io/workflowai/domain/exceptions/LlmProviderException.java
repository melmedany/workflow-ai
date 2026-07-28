package io.workflowai.domain.exceptions;

import io.workflowai.application.LlmProviderId;

public class LlmProviderException extends DomainException {

    public LlmProviderException(LlmProviderId providerId, String message) {
        this(providerId, message, null);
    }

    public LlmProviderException(LlmProviderId providerId, String message, Throwable cause) {
        super("Provider [%s]: %s".formatted(providerId, message), cause);
    }
}
