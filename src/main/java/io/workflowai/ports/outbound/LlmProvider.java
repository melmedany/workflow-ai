package io.workflowai.ports.outbound;

import io.workflowai.application.LlmProviderId;
import io.workflowai.domain.model.LlmRequest;

import java.util.Set;
import java.util.function.Consumer;

public interface LlmProvider {

    LlmProviderId getId();

    String stream(LlmRequest request, Consumer<String> tokenConsumer);

    String call(LlmRequest request);

    boolean supportsModel(String model);

    Set<String> supportedModels();
}
