package io.workflowai.domain.exceptions;

import io.workflowai.application.LLMProviderId;

public class LlmCallException extends LlmProviderException {

    public LlmCallException(LLMProviderId providerId, String message) {
        super(providerId, message);
    }

    public LlmCallException(LLMProviderId providerId, String message, Throwable cause) {
        super(providerId, message, cause);
    }
}
