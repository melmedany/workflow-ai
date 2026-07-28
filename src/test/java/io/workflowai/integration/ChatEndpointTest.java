package io.workflowai.integration;

import io.restassured.http.ContentType;
import io.workflowai.adapters.inbound.rest.dto.ConversationResponse;
import io.workflowai.adapters.inbound.rest.dto.EventType;
import io.workflowai.application.LlmProviderId;
import io.workflowai.application.LlmProviderRegistry;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.domain.model.ConversationMessageRole;
import io.workflowai.domain.model.LlmRequest;
import io.workflowai.ports.outbound.LlmProvider;
import io.workflowai.ports.outbound.MessageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Sql
class ChatEndpointTest extends IntegrationBase {

    // agent is defined via ChatEndpointTest.sql
    private final UUID AGENT_ID = UUID.fromString("1a907243-9428-41e3-a3d1-2c25ffd2a14f");

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private MessageStorage messageStorage;

    @MockitoBean
    private LlmProviderRegistry llmProviderRegistry;

    @BeforeEach
    void mockOllamaProvider() {
        LlmProvider ollama = new LlmProvider() {
            @Override
            public LlmProviderId getId() {
                return LlmProviderId.Ollama;
            }

            @Override
            public String stream(LlmRequest request, Consumer<String> tokenConsumer) {
                String response = "Test response";
                tokenConsumer.accept(response);
                return response;
            }

            @Override
            public String call(LlmRequest request) {
                return "{\"decisionMode\":\"GREET\",\"detectedTopics\":[],\"extractedIntent\":\"Hello\",\"clarificationQuestion\":null,\"reason\":\"Greeting\"}";
            }

            @Override
            public boolean supportsModel(String model) {
                return true;
            }

            @Override
            public java.util.Set<String> supportedModels() {
                return java.util.Set.of();
            }
        };
        when(llmProviderRegistry.get(any())).thenReturn(ollama);
        when(llmProviderRegistry.supportedLlmProvider()).thenReturn(java.util.Map.of(LlmProviderId.Ollama, java.util.Set.of()));
    }

    @Test
    void chat_persistsExactlyOneUserAndOneAgentMessagePerTurn() {
        ConversationResponse conversation = newConversation(AGENT_ID);

        List<ConversationMessage> messages =
                messageStorage.findByAgentIdAndConversationId(conversation.agentId(), conversation.id());

        long userMessages = messages.stream().filter(m -> m.role() == ConversationMessageRole.USER).count();
        long agentMessages = messages.stream().filter(m -> m.role() == ConversationMessageRole.AGENT).count();

        assertThat(userMessages).isEqualTo(1);
        assertThat(agentMessages).isEqualTo(1);
    }

    @Test
    void getAgents_returnsAllAgents() {
        given()
                .when()
                .get("/api/agents")
                .then()
                .statusCode(200)
                .body("$", not(empty()));
    }

    @Test
    void getConversations_returnsArrayForAgent() {
        given()
                .when()
                .get("/api/agents/{agentId}/conversations", AGENT_ID)
                .then()
                .statusCode(200);
    }

    @Test
    void deleteConversation_removesIt() {
        ConversationResponse conversation = newConversation(AGENT_ID);

        given()
                .when()
                .delete("/api/agents/{agentId}/conversations/{conversationId}", conversation.agentId(), conversation.id())
                .then()
                .statusCode(204);
    }

    @Test
    void deleteNonExistentConversation_isIdempotent() {
        given()
                .when()
                .delete("/api/agents/{agentId}/conversations/{conversationId}", AGENT_ID, UUID.randomUUID())
                .then()
                .statusCode(204);
    }

    @Test
    void getMessages_returnsAgentGreetingForNewConversation() {
        ConversationResponse conversation = newConversation(AGENT_ID);

        given()
                .when()
                .get("/api/agents/{agentId}/conversations/{conversationId}/messages", conversation.agentId(), conversation.id())
                .then()
                .statusCode(200);
    }

    private ConversationResponse newConversation(UUID agentId) {
        String rawStream = given()
                .contentType(ContentType.JSON)
                .body("{\"message\":\"Hello\"}")
                .when()
                .post("/api/agents/{agentId}/conversations/{id}/chat", agentId, "NEW_CONVERSATION")
                .then()
                .extract()
                .asString();

        return jsonMapper.readValue(extractEventData(EventType.CONVERSATION_CREATED, rawStream), ConversationResponse.class);
    }

    private String extractEventData(EventType eventType, String rawStream) {
        List<String> lines = Arrays.stream(rawStream.split("\\r?\\n"))
                .map(String::trim)
                .toList();

        String targetEventLine = "event:%s".formatted(eventType);

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(targetEventLine)) {
                if (i + 1 < lines.size() && lines.get(i + 1).startsWith("data:")) {
                    return lines.get(i + 1).substring(5).trim();
                }
            }
        }

        throw new AssertionError("Event '%s' with valid data block was not found in the stream.".formatted(eventType));
    }
}