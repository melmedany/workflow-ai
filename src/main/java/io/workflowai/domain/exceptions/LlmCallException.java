package io.workflowai.domain.exceptions;

public class LlmCallException extends LlmProviderException {

    public LlmCallException(String providerName, String message) {
        super(providerName, message);
    }

    public LlmCallException(String providerName, String message, Throwable cause) {
        super(providerName, message, cause);
    }
}
