package io.workflowai.adapters.outbound.providers.anthropic;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.workflowai.adapters.outbound.providers.AbstractLlmProvider;
import io.workflowai.application.LlmProviderId;
import io.workflowai.domain.exceptions.LlmCallException;
import io.workflowai.domain.model.LlmRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class AnthropicProvider extends AbstractLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private final AnthropicProperties properties;

    public AnthropicProvider(AnthropicProperties properties) {
        this.properties = properties;
        if (!properties.isConfigured()) {
            log.warn("[{}] is not fully configured", getId());
        }
    }

    @Override
    public LlmProviderId getId() {
        return LlmProviderId.Anthropic;
    }

    @Override
    public boolean supportsModel(String model) {
        return model != null && properties.supportedModels().contains(model);
    }

    @Override
    public Set<String> supportedModels() {
        return properties.supportedModels();
    }

    @Override
    public String stream(LlmRequest request, Consumer<String> tokenConsumer) {
        String model = resolveModel(request.model(), properties.defaultModel());
        List<ChatMessage> messages = buildMessages(request);
        StreamingChatModel streaming = model.equals(properties.defaultModel())
                ? getDefaultStreamingModel(properties.defaultModel(), properties.defaultTemperature())
                : buildStreamingModel(model, request.temperature());
        log.debug("Streaming with Anthropic model [{}]", model);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(LlmRequest request) {
        String model = resolveModel(request.model(), properties.defaultModel());
        List<ChatMessage> messages = buildMessages(request);
        ChatModel chat = model.equals(properties.defaultModel())
                ? getDefaultChatModel(properties.defaultModel(), properties.defaultTemperature())
                : buildChatModel(model, request.temperature());
        log.debug("Calling Anthropic model [{}]", model);
        try {
            return chat.chat(messages).aiMessage().text();
        } catch (Exception ex) {
            throw new LlmCallException(getId(), "Sync call failed for model [%s]".formatted(model), ex);
        }
    }

    @Override
    protected ChatModel buildChatModel(String model, double temperature) {
        return AnthropicChatModel.builder()
                .baseUrl(properties.baseUrl)
                .apiKey(properties.apiKey())
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    @Override
    protected StreamingChatModel buildStreamingModel(String model, double temperature) {
        return AnthropicStreamingChatModel.builder()
                .baseUrl(properties.baseUrl)
                .apiKey(properties.apiKey())
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    @ConfigurationProperties(prefix = "langchain4j.providers.anthropic")
    public record AnthropicProperties(String baseUrl, String apiKey, String defaultModel,
                                      double defaultTemperature, Set<String> supportedModels) {

        public boolean isConfigured() {
            return isConfigured(baseUrl) && isConfigured(apiKey);
        }

        private static boolean isConfigured(String value) {
            return value != null && !"not-configured".equals(value) && !value.isBlank();

        }
    }
}

