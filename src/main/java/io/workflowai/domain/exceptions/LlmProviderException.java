package io.workflowai.domain.exceptions;

import io.workflowai.application.LLMProviderId;

public class LlmProviderException extends DomainException {

    public LlmProviderException(LLMProviderId providerId, String message) {
        this(providerId, message, null);
    }

    public LlmProviderException(LLMProviderId providerId, String message, Throwable cause) {
        super("Provider [%s]: %s".formatted(providerId, message), cause);
    }
}
