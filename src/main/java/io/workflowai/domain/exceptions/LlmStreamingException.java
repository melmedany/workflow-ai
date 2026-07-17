package io.workflowai.domain.exceptions;

public class LlmStreamingException extends LlmProviderException {

    public LlmStreamingException(String providerName, String message) {
        super(providerName, message);
    }

    public LlmStreamingException(String providerName, String message, Throwable cause) {
        super(providerName, message, cause);
    }
}
