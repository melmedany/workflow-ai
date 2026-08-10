package io.workflowai.adapter.out.chat.provider;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.workflowai.application.execution.workflow.WorkflowPrompts;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.exceptions.ChatProviderStreamingException;
import io.workflowai.domain.exceptions.GuardrailBlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class AbstractChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractChatProvider.class);

    private final InputGuardrail inputGuardrail;
    private final OutputGuardrail outputGuardrail;

    protected ChatModel defaultChatModel;
    protected StreamingChatModel defaultStreamingModel;

    protected AbstractChatProvider(InputGuardrail inputGuardrail, OutputGuardrail outputGuardrail) {
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
    }

    protected String resolveModel(String requestedModel, String defaultModel) {
        if (supportsModel(requestedModel)) {
            log.debug("Resolved model [{}] for provider [{}]", requestedModel, getId());
            return requestedModel;
        }
        log.warn("Model [{}] not supported by provider [{}], falling back to default [{}]",
                requestedModel, getId(), defaultModel);
        return defaultModel;
    }

    protected List<ChatMessage> buildMessages(ChatCompletionRequest request) {
        UserMessage userMessage = UserMessage.from(request.message());
        if (!inputGuardrail.validate(userMessage).isSuccess()) {
            throw new GuardrailBlockedException(getId(), "Input blocked by guardrail");
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPromptWithMemory(request)));
        messages.add(userMessage);
        log.debug("Built {} messages for provider [{}]", messages.size(), getId());
        return messages;
    }

    /**
     * Extracts the final text from a non-streaming response, applying the same output-guardrail
     * substitution as {@link #doStream}.
     */
    protected String extractText(ChatResponse response) {
        return applyOutputGuardrail(response.aiMessage().text());
    }

    private String applyOutputGuardrail(String text) {
        if (!outputGuardrail.validate(AiMessage.from(text)).isSuccess()) {
            log.warn("[{}] Output guardrail blocked generated response — matched blocklist term", getId());
            return WorkflowPrompts.GUARDRAIL_FALLBACK_MESSAGE;
        }
        return text;
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

    private String systemPromptWithMemory(ChatCompletionRequest request) {
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
                log.debug("Streaming completed on provider [{}]", getId());
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
            throw new ChatProviderStreamingException(getId(), "Streaming interrupted", ex);
        }

        if (errorRef.get() != null) {
            throw new ChatProviderStreamingException(getId(),
                    "Streaming failed: " + errorRef.get().getMessage(), errorRef.get());
        }

        return applyOutputGuardrail(buffer.toString());
    }
}
