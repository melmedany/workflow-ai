package io.workflowai.domain.exceptions;

import io.workflowai.application.LLMProviderId;

public class LlmStreamingException extends LlmProviderException {

    public LlmStreamingException(LLMProviderId providerId, String message) {
        super(providerId, message);
    }

    public LlmStreamingException(LLMProviderId providerId, String message, Throwable cause) {
        super(providerId, message, cause);
    }
}
