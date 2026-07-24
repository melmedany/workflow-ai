package io.workflowai.adapters.outbound.providers.ollama;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import io.workflowai.adapters.outbound.providers.AbstractLlmProvider;
import io.workflowai.application.LLMProviderId;
import io.workflowai.domain.exceptions.LlmCallException;
import io.workflowai.domain.model.LLMRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

@Component
public class OllamaProvider extends AbstractLlmProvider {

    private static final Logger log = LoggerFactory.getLogger(OllamaProvider.class);
    // TODO make configurable
    private static final Set<String> SUPPORTED_MODELS = Set.of(
            "deepseek-r1:8b", "deepseek-r1:32b", "deepseek-r1:671b",
            "mistral:7b", "devstral:24b", "mistral-large",
            "gemma4:e2b", "gemma4:12b", "gemma4:26b",
            "qwen3.5:7b", "qwen3.6-coder:27b", "qwen3.5:122b",
            "llama3.2:3b", "llama3.1:8b", "llama3.3:70b",
            "phi4:mini", "phi5:14b", "codellama:7b", "starcoder2:15b"
    );
    private final RestClient restClient;
    private final OllamaProperties properties;
    private final JsonMapper jsonMapper;

    public OllamaProvider(RestClient.Builder restClientBuilder, OllamaProperties properties, JsonMapper jsonMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        if (!properties.isConfigured()) {
            log.warn("[{}] is not fully configured", getId());
        }
    }

    @Override
    public LLMProviderId getId() {
        return LLMProviderId.Ollama;
    }

    @Override
    public boolean supportsModel(String model) {
        return model != null && SUPPORTED_MODELS.stream().anyMatch(model::startsWith);
    }

    @Override
    public Set<String> supportedModels() {
        return SUPPORTED_MODELS;
    }

    @Override
    public String stream(LLMRequest request, Consumer<String> tokenConsumer) {
        String model = resolveModel(request.model(), properties.defaultModel());

        ensureModelAvailable(model);

        List<ChatMessage> messages = buildMessages(request);
        StreamingChatModel streaming = model.equals(properties.defaultModel())
                ? getDefaultStreamingModel(properties.defaultModel(), properties.defaultTemperature())
                : buildStreamingModel(model, request.temperature());
        log.debug("Streaming with Ollama model [{}]", model);
        return doStream(streaming, messages, tokenConsumer);
    }

    @Override
    public String call(LLMRequest request) {
        String model = resolveModel(request.model(), properties.defaultModel());

        ensureModelAvailable(model);

        List<ChatMessage> messages = buildMessages(request);
        ChatModel chat = model.equals(properties.defaultModel())
                ? getDefaultChatModel(properties.defaultModel(), properties.defaultTemperature())
                : buildChatModel(model, request.temperature());
        log.debug("Calling Ollama model [{}]", model);
        try {
            return chat.chat(messages).aiMessage().text();
        } catch (Exception ex) {
            throw new LlmCallException(getId(), "Sync call failed for model [%s]".formatted(model), ex);
        }
    }

    @Override
    protected ChatModel buildChatModel(String model, double temperature) {
        return OllamaChatModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    @Override
    protected StreamingChatModel buildStreamingModel(String model, double temperature) {
        return OllamaStreamingChatModel.builder()
                .baseUrl(properties.baseUrl())
                .modelName(model)
                .temperature(temperature)
                .build();
    }

    /**
     * Ensures the specified model is available for use. Not optimal, but works.
     */
    private void ensureModelAvailable(String model) {
        if (!isModelAvailable(model)) {
            throw new LlmCallException(getId(), "Please ensure Ollama is running and download the model by executing ollama pull %s".formatted(model));
        }
    }

    private boolean isModelAvailable(String model) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(properties.baseUrl()+ "/api/tags")
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode() != HttpStatus.OK) return false;

            return response.getBody() != null && hasModel(response.getBody(), model);
        } catch (Exception ex) {
            throw new LlmCallException(getId(), "Failed to pull model [%s]".formatted(model), ex);
        }
    }

    private boolean hasModel(String json, String target) {
        JsonNode models = jsonMapper.readTree(json).get("models");
        return StreamSupport.stream(models.spliterator(), false)
                .anyMatch(node -> target.equals(node.get("model").asString()));
    }

    @ConfigurationProperties(prefix = "langchain4j.providers.ollama")
    public record OllamaProperties(String baseUrl, String defaultModel, double defaultTemperature) {

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank() && !baseUrl.equals("not-configured");
        }
    }
}