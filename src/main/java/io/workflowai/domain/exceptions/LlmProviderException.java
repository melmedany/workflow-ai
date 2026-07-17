package io.workflowai.domain.exceptions;

public class LlmProviderException extends DomainException {

    private final String providerName;

    public LlmProviderException(String providerName, String message) {
        super("Provider [%s]: %s".formatted(providerName, message));
        this.providerName = providerName;
    }

    public LlmProviderException(String providerName, String message, Throwable cause) {
        super("Provider [%s]: %s".formatted(providerName, message), cause);
        this.providerName = providerName;
    }

    public String getProviderName() {
        return providerName;
    }
}
