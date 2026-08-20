package io.workflowai.adapter.out.chat.provider.anthropic;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.workflowai.adapter.out.chat.provider.AbstractChatProvider;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.exceptions.ChatProviderCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class AnthropicProvider extends AbstractChatProvider {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProvider.class);
    private final AnthropicProperties properties;

    public AnthropicProvider(InputGuardrail inputGuardrail, OutputGuardrail outputGuardrail, AnthropicProperties properties) {
        super(inputGuardrail, outputGuardrail);
        this.properties = properties;
        if (!properties.isConfigured()) {
            log.warn("[{}] is not fully configured", getId());
        }
    }

    @Override
    public ChatProviderId getId() {
        return ChatProviderId.Anthropic;
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
    public String stream(ChatCompletionRequest request, Consumer<String> tokenConsumer) {
        String model = resolveModel(request.model());
        List<ChatMessage> messages = buildMessages(request);
        StreamingChatModel streaming = model.equals(properties.defaultModel())
                ? getDefaultStreamingModel(properties.defaultModel(), properties.defaultTemperature())
                : buildStreamingModel(model, request.temperature());
        log.debug("Streaming with Anthropic model [{}]", model);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(ChatCompletionRequest request) {
        String model = resolveModel(request.model());
        List<ChatMessage> messages = buildMessages(request);
        ChatModel chatModel = model.equals(properties.defaultModel())
                ? getDefaultChatModel(properties.defaultModel(), properties.defaultTemperature())
                : buildChatModel(model, request.temperature());
        log.debug("Calling Anthropic model [{}]", model);
        try {
            return extractText(chatModel.chat(messages));
        } catch (Exception ex) {
            throw new ChatProviderCallException(getId(), "Sync call failed for model [%s]".formatted(model), ex);
        }
    }

    @Override
    protected ChatModel buildChatModel(String model, double temperature) {
        if (chatModelMap.containsKey(model)) return chatModelMap.get(model);

        ChatModel chatModel = AnthropicChatModel.builder()
                .baseUrl(properties.baseUrl)
                .apiKey(properties.apiKey())
                .modelName(model)
                .temperature(temperature)
                .build();

        chatModelMap.put(model, chatModel);

        return chatModel;
    }

    @Override
    protected StreamingChatModel buildStreamingModel(String model, double temperature) {
        if (streamingChatModelMap.containsKey(model)) return streamingChatModelMap.get(model);

        StreamingChatModel streamingChatModel = AnthropicStreamingChatModel.builder()
                .baseUrl(properties.baseUrl)
                .apiKey(properties.apiKey())
                .modelName(model)
                .temperature(temperature)
                .build();

        streamingChatModelMap.put(model, streamingChatModel);

        return streamingChatModel;
    }

    @ConfigurationProperties(prefix = "langchain4j.anthropic")
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

