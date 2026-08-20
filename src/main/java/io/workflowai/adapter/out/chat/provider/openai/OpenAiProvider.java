package io.workflowai.adapter.out.chat.provider.openai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.workflowai.adapter.out.chat.provider.AbstractOpenAiProvider;
import io.workflowai.domain.exceptions.ChatProviderCallException;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.application.port.out.ChatCompletionRequest;
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
    private final OpenAiProperties properties;

    public OpenAiProvider(InputGuardrail inputGuardrail, OutputGuardrail outputGuardrail, OpenAiProperties properties) {
        super(inputGuardrail, outputGuardrail, properties.baseUrl(), properties.apiKey(), properties.defaultModel(), properties.defaultTemperature());
        this.properties = properties;
        if (!properties.isConfigured()) {
            log.warn("[{}] is not fully configured", getId());
        }
    }

    @Override
    public ChatProviderId getId() {
        return ChatProviderId.OpenAI;
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
        String resolvedModel = resolveModel(request.model());
        List<ChatMessage> messages = buildMessages(request);
        StreamingChatModel streaming = resolvedModel.equals(properties.defaultModel())
                ? getDefaultStreamingModel(properties.defaultModel, properties.defaultTemperature)
                : buildStreamingModel(resolvedModel, request.temperature());
        log.debug("Streaming with {} model [{}]", getId(), resolvedModel);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(ChatCompletionRequest request) {
        String resolvedModel = resolveModel(request.model());
        List<ChatMessage> messages = buildMessages(request);
        ChatModel chatModel = resolvedModel.equals(properties.defaultModel())
                ? getDefaultChatModel(properties.defaultModel, properties.defaultTemperature)
                : buildChatModel(resolvedModel, request.temperature());
        log.debug("Calling {} model [{}]", getId(), resolvedModel);
        try {
            return extractText(chatModel.chat(messages));
        } catch (Exception ex) {
            throw new ChatProviderCallException(getId(), "Sync call failed for model [%s]".formatted(resolvedModel), ex);
        }
    }

    @ConfigurationProperties(prefix = "langchain4j.openai")
    public record OpenAiProperties(String baseUrl, String apiKey, String defaultModel,
                                   double defaultTemperature, Set<String> supportedModels) {

        public boolean isConfigured() {
            return isConfigured(baseUrl) && isConfigured(apiKey);
        }

        private static boolean isConfigured(String value) {
            return value != null && !"not-configured".equals(value) && !value.isBlank();
        }
    }
}
