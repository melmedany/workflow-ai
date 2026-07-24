package io.workflowai.ports.outbound;

import io.workflowai.application.LLMProviderId;
import io.workflowai.domain.model.LLMRequest;

import java.util.Set;
import java.util.function.Consumer;

public interface LLLMProvider {

    LLMProviderId getId();

    String stream(LLMRequest request, Consumer<String> tokenConsumer);

    String call(LLMRequest request);

    boolean supportsModel(String model);

    Set<String> supportedModels();
}
