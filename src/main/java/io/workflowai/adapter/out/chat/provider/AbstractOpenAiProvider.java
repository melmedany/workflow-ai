package io.workflowai.adapter.out.chat.provider;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.workflowai.domain.exceptions.ChatProviderCallException;
import io.workflowai.application.port.out.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared base for OpenAI-compatible third-party providers (e.g. bonzai) using custom base URLs.
 */
public abstract class AbstractOpenAiProvider extends AbstractChatProvider {

    private static final Logger log = LoggerFactory.getLogger(AbstractOpenAiProvider.class);

    private final String baseUrl;
    private final String apiKey;
    protected final String defaultModel;
    private final double defaultTemperature;

    protected AbstractOpenAiProvider(InputGuardrail inputGuardrail, OutputGuardrail outputGuardrail,
                                      String baseUrl, String apiKey, String defaultModel, double defaultTemperature) {
        super(inputGuardrail, outputGuardrail);
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.defaultTemperature = defaultTemperature;
    }

    @Override
    public String stream(ChatRequest request, Consumer<String> tokenConsumer) {
        String resolvedModel = resolveModel(request.model(), this.defaultModel);
        List<ChatMessage> messages = buildMessages(request);
        StreamingChatModel streaming = resolvedModel.equals(this.defaultModel)
                ? getDefaultStreamingModel(this.defaultModel, this.defaultTemperature)
                : buildStreamingModel(resolvedModel, request.temperature());
        log.debug("Streaming with {} model [{}]", getId(), resolvedModel);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(ChatRequest request) {
        String resolvedModel = resolveModel(request.model(), this.defaultModel);
        List<ChatMessage> messages = buildMessages(request);
        ChatModel chat = resolvedModel.equals(this.defaultModel)
                ? getDefaultChatModel(this.defaultModel, this.defaultTemperature)
                : buildChatModel(resolvedModel, request.temperature());
        log.debug("Calling {} model [{}]", getId(), resolvedModel);
        try {
            return extractText(chat.chat(messages));
        } catch (Exception ex) {
            throw new ChatProviderCallException(getId(), "Sync call failed for model [%s]".formatted(resolvedModel), ex);
        }
    }

    @Override
    protected ChatModel buildChatModel(String model, double temperature) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    @Override
    protected StreamingChatModel buildStreamingModel(String model, double temperature) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(temperature)
                .build();
    }
}
