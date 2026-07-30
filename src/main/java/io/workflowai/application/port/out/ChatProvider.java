package io.workflowai.application.port.out;

import io.workflowai.domain.agent.ChatProviderId;
import java.util.Set;
import java.util.function.Consumer;

public interface ChatProvider {

    ChatProviderId getId();

    String stream(ChatCompletionRequest request, Consumer<String> tokenConsumer);

    String call(ChatCompletionRequest request);

    boolean supportsModel(String model);

    Set<String> supportedModels();
}