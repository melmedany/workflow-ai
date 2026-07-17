package io.workflowai.ports.outbound;

import io.workflowai.domain.model.LlmRequest;

import java.util.Set;
import java.util.function.Consumer;

public interface LlmProviderPort {

    String getProviderName();

    /**
     * Streams tokens to the consumer and returns the full accumulated response.
     */
    String stream(LlmRequest request, Consumer<String> tokenConsumer);

    String call(LlmRequest request);

    boolean isConfigured();

    boolean supportsModel(String model);

    Set<String> supportedModels();
}
