package io.workflowai.adapters.outbound.providers;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.workflowai.domain.exceptions.LlmCallException;
import io.workflowai.domain.model.LlmRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Shared base for OpenAI-compatible third-party providers (e.g. bonzai) using custom base URLs.
 */
public abstract class AbstractOpenAiProvider extends AbstractLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractOpenAiProvider.class);

    private final String baseUrl;
    private final String apiKey;
    protected final String model;
    private final ChatModel chatModel;
    private final StreamingChatModel streamingModel;

    protected AbstractOpenAiProvider(String baseUrl, String apiKey, String model, double temperature) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.chatModel = buildChatModel(model, temperature);
        this.streamingModel = buildStreamingModel(model, temperature);
        log.info("{} provider initialised — base-url: {}, default-model: {}", getProviderName(), baseUrl, model);
    }

    @Override
    public String stream(LlmRequest request, Consumer<String> tokenConsumer) {
        String model = resolveModel(request.model(), this.model);
        var messages = buildMessages(request);
        var streaming = model.equals(this.model)
                ? streamingModel
                : buildStreamingModel(model, request.temperature());
        log.debug("Streaming with {} model [{}]", getProviderName(), model);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(LlmRequest request) {
        String model = resolveModel(request.model(), this.model);
        var messages = buildMessages(request);
        var chat = model.equals(this.model)
                ? chatModel
                : buildChatModel(model, request.temperature());
        log.debug("Calling {} model [{}]", getProviderName(), model);
        try {
            return chat.chat(messages).aiMessage().text();
        } catch (Exception e) {
            throw new LlmCallException(getProviderName(),
                    "Sync call failed for model [%s]".formatted(model), e);
        }
    }

    private ChatModel buildChatModel(String model, double temperature) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    private StreamingChatModel buildStreamingModel(String model, double temperature) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .build();
    }
}
