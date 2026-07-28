package io.workflowai.adapters.outbound.providers;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.workflowai.domain.exceptions.LlmStreamingException;
import io.workflowai.domain.model.LlmRequest;
import io.workflowai.ports.outbound.LlmProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AbstractLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractLlmProvider.class);

    protected ChatModel defaultChatModel;
    protected StreamingChatModel defaultStreamingModel;

    protected String resolveModel(String requestedModel, String defaultModel) {
        if (!supportsModel(requestedModel)) {
            log.warn("Model [{}] not supported by provider [{}], falling back to default [{}]",
                    requestedModel, getId(), defaultModel);
            return defaultModel;
        }
        log.debug("Resolved model [{}] for provider [{}]", requestedModel, getId());
        return requestedModel;
    }

    protected List<ChatMessage> buildMessages(LlmRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPromptWithMemory(request)));
        messages.add(UserMessage.from(request.message()));
        log.debug("Built {} messages for provider [{}]", messages.size(), getId());
        return messages;
    }

    protected ChatModel getDefaultChatModel(String defaultModel, double defaultTemperature) {
        if (defaultChatModel == null) {
            defaultChatModel = buildChatModel(defaultModel, defaultTemperature);
            log.debug("[{}] initialised: {}", getId(), defaultModel);
        }
        return defaultChatModel;
    }

    protected StreamingChatModel getDefaultStreamingModel(String defaultModel, double defaultTemperature) {
        if (defaultStreamingModel == null) {
            defaultStreamingModel = buildStreamingModel(defaultModel, defaultTemperature);
        }
        return defaultStreamingModel;
    }

    abstract protected ChatModel buildChatModel(String model, double temperature);

    abstract protected StreamingChatModel buildStreamingModel(String model, double temperature);

    private String systemPromptWithMemory(LlmRequest request) {
        if (request.memoryContext() == null || request.memoryContext().isBlank()) {
            return request.systemPrompt();
        }
        return request.systemPrompt() + "\n\nConversation memory:\n" + request.memoryContext();
    }

    protected String doStream(StreamingChatModel streamingModel,
                              List<ChatMessage> messages,
                              Consumer<String> tokenConsumer) {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder buffer = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        log.debug("Starting streaming request on provider [{}]", getId());

        streamingModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                tokenConsumer.accept(token);
                buffer.append(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                log.debug("Streaming completed on provider [{}], chars received: {}",
                        getId(), buffer.length());
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                log.warn("Streaming error on provider [{}]: {}", getId(), error.getMessage());
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LlmStreamingException(getId(), "Streaming interrupted", ex);
        }

        if (errorRef.get() != null) {
            throw new LlmStreamingException(getId(),
                    "Streaming failed: " + errorRef.get().getMessage(), errorRef.get());
        }

        return buffer.toString();
    }
}
