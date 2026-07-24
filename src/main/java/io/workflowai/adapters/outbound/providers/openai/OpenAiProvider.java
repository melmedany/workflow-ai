package io.workflowai.adapters.outbound.providers.openai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.workflowai.adapters.outbound.providers.AbstractOpenAiProvider;
import io.workflowai.application.LLMProviderId;
import io.workflowai.domain.exceptions.LlmCallException;
import io.workflowai.domain.model.LLMRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Component
public class OpenAiProvider extends AbstractOpenAiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    // TODO make configurable
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "gpt-5.6-luna", "gpt-5.6-terra", "gpt-5.6-sol",
            "gpt-5.5", "gpt-5.4", "gpt-5.1", "gpt-5", "gpt-5-mini", "gpt-5-nano",
            "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
            "o3", "o4-mini", "o3-pro");

    private final OpenAiProperties properties;

    public OpenAiProvider(OpenAiProperties properties) {
        super(properties.baseUrl(), properties.apiKey(), properties.defaultModel(), properties.defaultTemperature());
        this.properties = properties;
        if (!properties.isConfigured()) {
            log.warn("[{}] is not fully configured", getId());
        }
    }

    @Override
    public LLMProviderId getId() {
        return LLMProviderId.OpenAI;
    }

    @Override
    public boolean supportsModel(String model) {
        return model != null && SUPPORTED_MODELS.stream().anyMatch(model::equals);
    }

    @Override
    public Set<String> supportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public String stream(LLMRequest request, Consumer<String> tokenConsumer) {
        String resolvedModel = resolveModel(request.model(), properties.defaultModel());
        List<ChatMessage> messages = buildMessages(request);
        StreamingChatModel streaming = resolvedModel.equals(properties.defaultModel())
                ? getDefaultStreamingModel(properties.defaultModel, properties.defaultTemperature)
                : buildStreamingModel(resolvedModel, request.temperature());
        log.debug("Streaming with {} model [{}]", getId(), resolvedModel);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(LLMRequest request) {
        String resolvedModel = resolveModel(request.model(), properties.defaultModel());
        List<ChatMessage> messages = buildMessages(request);
        ChatModel chat = resolvedModel.equals(properties.defaultModel())
                ? getDefaultChatModel(properties.defaultModel, properties.defaultTemperature)
                : buildChatModel(resolvedModel, request.temperature());
        log.debug("Calling {} model [{}]", getId(), resolvedModel);
        try {
            return chat.chat(messages).aiMessage().text();
        } catch (Exception ex) {
            throw new LlmCallException(getId(), "Sync call failed for model [%s]".formatted(resolvedModel), ex);
        }
    }

    @ConfigurationProperties(prefix = "langchain4j.providers.openai")
    public record OpenAiProperties(String baseUrl, String apiKey, String defaultModel, double defaultTemperature) {

        public boolean isConfigured() {
            return isConfigured(baseUrl) && isConfigured(apiKey);
        }

        private static boolean isConfigured(String value) {
            return value != null && !"not-configured".equals(value) && !value.isBlank();
        }
    }
}
