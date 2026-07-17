package io.workflowai.adapters.outbound.providers;

import io.workflowai.domain.exceptions.LlmStreamingException;
import io.workflowai.domain.exceptions.LlmProviderException;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.domain.model.ConversationMessageRole;
import io.workflowai.domain.model.LlmRequest;
import io.workflowai.ports.outbound.LlmProviderPort;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AbstractLlmProvider implements LlmProviderPort {

    private static final Logger log = LoggerFactory.getLogger(AbstractLlmProvider.class);

    protected String resolveModel(String requestedModel, String defaultModel) {
        if (!supportsModel(requestedModel)) {
            log.warn("Model [{}] not supported by provider [{}], falling back to default [{}]",
                    requestedModel, getProviderName(), defaultModel);
            return defaultModel;
        }
        log.debug("Resolved model [{}] for provider [{}]", requestedModel, getProviderName());
        return requestedModel;
    }

    protected List<ChatMessage> buildMessages(LlmRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(request.systemPrompt()));
        for (ConversationMessage msg : request.history()) {
            if (ConversationMessageRole.USER.equals(msg.role())) {
                messages.add(UserMessage.from(msg.content()));
            } else {
                messages.add(AiMessage.from(msg.content()));
            }
        }
        messages.add(UserMessage.from(request.userMessage()));
        log.debug("Built {} messages for provider [{}] (history size: {})",
                messages.size(), getProviderName(), request.history().size());
        return messages;
    }

    protected String doStream(StreamingChatModel streamingModel,
                              List<ChatMessage> messages,
                              Consumer<String> tokenConsumer) {
        var latch = new CountDownLatch(1);
        var buffer = new StringBuilder();
        var errorRef = new AtomicReference<Throwable>();

        log.debug("Starting streaming request on provider [{}]", getProviderName());

        streamingModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                tokenConsumer.accept(token);
                buffer.append(token);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                log.debug("Streaming completed on provider [{}], chars received: {}",
                        getProviderName(), buffer.length());
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                log.warn("Streaming error on provider [{}]: {}", getProviderName(), error.getMessage());
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmStreamingException(getProviderName(), "Streaming interrupted", e);
        }

        if (errorRef.get() != null) {
            throw new LlmStreamingException(getProviderName(),
                    "Streaming failed: " + errorRef.get().getMessage(), errorRef.get());
        }

        return buffer.toString();
    }
}
