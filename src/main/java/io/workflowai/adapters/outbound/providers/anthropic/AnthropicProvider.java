package io.workflowai.adapters.outbound.providers.anthropic;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.workflowai.adapters.outbound.providers.AbstractLlmProvider;
import io.workflowai.domain.exceptions.LlmCallException;
import io.workflowai.domain.model.LlmRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Consumer;

@Component
public class AnthropicProvider extends AbstractLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    // TODO make configurable
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "claude-haiku-4-5", "claude-sonnet-4-6", "claude-sonnet-5",
            "claude-opus-4-7", "claude-opus-4-8"
    );

    private final AnthropicProperties properties;
    private ChatModel defaultChatModel;
    private StreamingChatModel defaultStreamingModel;

    public AnthropicProvider(AnthropicProperties properties) {
        this.properties = properties;
        if (!properties.isConfigured()) {
            log.warn("Anthropic provider is not fully configured");
        }
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public boolean supportsModel(String model) {
        return model != null && SUPPORTED_MODELS.contains(model);
    }

    @Override
    public Set<String> supportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public String stream(LlmRequest request, Consumer<String> tokenConsumer) {
        String model = resolveModel(request.model(), properties.defaultModel());
        var messages = buildMessages(request);
        var streaming = model.equals(properties.defaultModel())
                ? getDefaultStreamingModel()
                : buildStreamingModel(model, request.temperature());
        log.debug("Streaming with Anthropic model [{}]", model);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(LlmRequest request) {
        String model = resolveModel(request.model(), properties.defaultModel());
        var messages = buildMessages(request);
        var chat = model.equals(properties.defaultModel())
                ? getDefaultChatModel()
                : buildChatModel(model, request.temperature());
        log.debug("Calling Anthropic model [{}]", model);
        try {
            return chat.chat(messages).aiMessage().text();
        } catch (Exception e) {
            throw new LlmCallException(getProviderName(), "Sync call failed for model [%s]".formatted(model), e);
        }
    }

    private ChatModel getDefaultChatModel() {
        if (defaultChatModel == null) {
            defaultChatModel = buildChatModel(properties.defaultModel(), properties.temperature());
            log.info("{} provider initialised: {}", getProviderName(), properties.defaultModel());
        }
        return defaultChatModel;
    }

    private StreamingChatModel getDefaultStreamingModel() {
        if (defaultStreamingModel == null) {
            defaultStreamingModel = buildStreamingModel(properties.defaultModel(), properties.temperature());
        }
        return defaultStreamingModel;
    }

    private ChatModel buildChatModel(String model, double temperature) {
        return AnthropicChatModel.builder()
                .apiKey(properties.apiKey())
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    private StreamingChatModel buildStreamingModel(String model, double temperature) {
        return AnthropicStreamingChatModel.builder()
                .apiKey(properties.apiKey())
                .modelName(model)
                .temperature(temperature)
                .build();
    }
}
