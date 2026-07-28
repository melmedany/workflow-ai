package io.workflowai.domain.exceptions;

import io.workflowai.application.LlmProviderId;

public class LlmStreamingException extends LlmProviderException {

    public LlmStreamingException(LlmProviderId providerId, String message) {
        this(providerId, message, null);
    }

    public LlmStreamingException(LlmProviderId providerId, String message, Throwable cause) {
        super(providerId, message, cause);
    }
}
