package io.workflowai.domain.exceptions;

import io.workflowai.application.LlmProviderId;

public class LlmCallException extends LlmProviderException {

    public LlmCallException(LlmProviderId providerId, String message) {
        super(providerId, message);
    }

    public LlmCallException(LlmProviderId providerId, String message, Throwable cause) {
        super(providerId, message, cause);
    }
}
