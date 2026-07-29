package io.workflowai.application.port.out;

import io.workflowai.domain.agent.ChatProviderId;
import java.util.Set;
import java.util.function.Consumer;

public interface ChatProvider {

    ChatProviderId getId();

    String stream(ChatRequest request, Consumer<String> tokenConsumer);

    String call(ChatRequest request);

    boolean supportsModel(String model);

    Set<String> supportedModels();
}