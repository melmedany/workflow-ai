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
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.workflowai.application.execution.workflow.WorkflowPrompts;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.exceptions.ChatProviderCallException;
import io.workflowai.domain.exceptions.ChatProviderStreamingException;
import io.workflowai.domain.exceptions.GuardrailBlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public abstract class AbstractChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractChatProvider.class);
    private static final long STREAM_TIMEOUT_MILLIS = 90000;

    private final InputGuardrail inputGuardrail;
    private final OutputGuardrail outputGuardrail;

    protected final Map<String, ChatModel> chatModelMap = new ConcurrentHashMap<>();
    protected final Map<String, StreamingChatModel> streamingChatModelMap = new ConcurrentHashMap<>();

    protected ChatModel defaultChatModel;
    protected StreamingChatModel defaultStreamingModel;

    protected AbstractChatProvider(InputGuardrail inputGuardrail, OutputGuardrail outputGuardrail) {
        this.inputGuardrail = inputGuardrail;
        this.outputGuardrail = outputGuardrail;
    }

    protected String resolveModel(String requestedModel) {
        if (supportsModel(requestedModel)) {
            log.debug("Resolved model [{}] for provider [{}]", requestedModel, getId());
            return requestedModel;
        }
        throw new ChatProviderCallException(getId(), "Unsupported model [%s] for provider [%s]. Supported models: %s"
                .formatted(requestedModel, getId(), supportedModels()));
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
            log.warn("[{}] Output guardrail blocked generated response matched blocklist term", getId());
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

    protected String doStream(StreamingChatModel streamingModel, List<ChatMessage> messages, Consumer<String> tokenConsumer) {
        log.debug("Starting streaming request on provider [{}]", getId());

        StringBuilder buffer = new StringBuilder();
        CompletableFuture<Void> completion = new CompletableFuture<>();

        streamingModel.chat(messages, new ResponseHandler(getId(),buffer, completion,
                tokenConsumer, System.currentTimeMillis() + STREAM_TIMEOUT_MILLIS));

        try {
            completion.orTimeout(STREAM_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).join();
            return applyOutputGuardrail(buffer.toString());
        } catch (CompletionException ex) {
            if (ex.getCause() != null && ex.getCause() instanceof TimeoutException cause) {
                throw new ChatProviderStreamingException(getId(), "Streaming timed out after %s".formatted(STREAM_TIMEOUT_MILLIS), cause);
            }

            throw new ChatProviderStreamingException(getId(), "Streaming failed.", ex);
        }
    }

    private record ResponseHandler(ChatProviderId chatProviderId, StringBuilder buffer, CompletableFuture<Void> completion,
                                   Consumer<String> tokenConsumer, long timeoutMillis) implements StreamingChatResponseHandler {

        @Override
        public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
            if (shouldCancel()) {
                context.streamingHandle().cancel();
            }
            String token = partialResponse.text();
            buffer.append(token);
            tokenConsumer.accept(token);
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            log.debug("Streaming completed on provider [{}]", chatProviderId);
            completion.complete(null);
        }

        @Override
        public void onError(Throwable error) {
            log.warn("Streaming error on provider [{}]: {}", chatProviderId, error.getMessage());
            completion.completeExceptionally(error);
        }

        private boolean shouldCancel() {
           return System.currentTimeMillis() > timeoutMillis;
        }
    }
}