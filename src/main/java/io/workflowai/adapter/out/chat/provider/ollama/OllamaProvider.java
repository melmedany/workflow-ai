package io.workflowai.adapter.out.chat.provider.ollama;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.workflowai.adapter.out.chat.provider.AbstractChatProvider;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.exceptions.ChatProviderCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

@Component
public class OllamaProvider extends AbstractChatProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    private final Set<String> modelAvailability = new HashSet<>();
    private final RestClient restClient;
    private final OllamaProperties properties;
    private final JsonMapper jsonMapper;

    public OllamaProvider(InputGuardrail inputGuardrail, OutputGuardrail outputGuardrail,
                          RestClient.Builder restClientBuilder, OllamaProperties properties, JsonMapper jsonMapper) {
        super(inputGuardrail, outputGuardrail);
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        if (!properties.isConfigured()) {
            log.warn("[{}] is not fully configured", getId());
        }
    }

    @Override
    public ChatProviderId getId() {
        return ChatProviderId.Ollama;
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

        ensureModelAvailable(model);

        List<ChatMessage> messages = buildMessages(request);
        StreamingChatModel streaming = model.equals(properties.defaultModel())
                ? getDefaultStreamingModel(properties.defaultModel(), properties.defaultTemperature())
                : buildStreamingModel(model, request.temperature());
        log.debug("Streaming with Ollama model [{}]", model);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(ChatCompletionRequest request) {
        String model = resolveModel(request.model());

        ensureModelAvailable(model);

        List<ChatMessage> messages = buildMessages(request);
        ChatModel chatModel = model.equals(properties.defaultModel())
                ? getDefaultChatModel(properties.defaultModel(), properties.defaultTemperature())
                : buildChatModel(model, request.temperature());
        log.debug("Calling Ollama model [{}]", model);
        try {
            return extractText(chatModel.chat(messages));
        } catch (RuntimeException ex) {
            throw new ChatProviderCallException(getId(), "Sync call failed for model [%s]".formatted(model), ex);
        }
    }

    @Override
    protected ChatModel buildChatModel(String model, double temperature) {
        ChatModel chatModel = chatModelMap.get(model);

        if (chatModel == null) {
            chatModel = OllamaChatModel.builder()
                    .baseUrl(properties.baseUrl())
                    .modelName(model)
                    .temperature(temperature)
                    .timeout(properties.timeout())
                    .build();

            chatModelMap.put(model, chatModel);
        }

        return chatModel;
    }

    @Override
    protected StreamingChatModel buildStreamingModel(String model, double temperature) {
        StreamingChatModel streamingChatModel = streamingChatModelMap.get(model);

        if (streamingChatModel == null) {
            streamingChatModel = OllamaStreamingChatModel.builder()
                    .baseUrl(properties.baseUrl())
                    .modelName(model)
                    .temperature(temperature)
                    .timeout(properties.timeout())
                    .build();

            streamingChatModelMap.put(model, streamingChatModel);
        }

        return streamingChatModel;
    }

    /**
     * Ensures the specified model is available for use. Not optimal, but works.
     */
    private void ensureModelAvailable(String model) {
        if (!isModelAvailable(model)) {
            throw new ChatProviderCallException(getId(), "Please ensure Ollama is running and download the model by executing ollama pull %s".formatted(model));
        }
    }

    private boolean isModelAvailable(String model) {
        if (modelAvailability.contains(model)) return true;

        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(properties.baseUrl() + "/api/tags")
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode() != HttpStatus.OK) return false;

            boolean modelAvailable = response.getBody() != null && hasModel(response.getBody(), model);

            if (modelAvailable) {
                modelAvailability.add(model);
            }

            return modelAvailable;
        } catch (RuntimeException ex) {
            throw new ChatProviderCallException(getId(), "Failed to pull model [%s]".formatted(model), ex);
        }
    }

    private boolean hasModel(String json, String target) {
        JsonNode models = jsonMapper.readTree(json).get("models");
        return StreamSupport.stream(models.spliterator(), false)
                .anyMatch(node -> target.equals(node.get("model").asString()));
    }

    @ConfigurationProperties(prefix = "langchain4j.ollama")
    public record OllamaProperties(String baseUrl, String defaultModel, double defaultTemperature,
                                   Set<String> supportedModels, Duration timeout) {

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank() && !baseUrl.equals("not-configured");
        }
    }
}