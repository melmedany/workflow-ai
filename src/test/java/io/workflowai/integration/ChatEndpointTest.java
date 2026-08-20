package io.workflowai.integration;

import io.restassured.http.ContentType;
import io.workflowai.adapter.in.rest.dto.ConversationResponse;
import io.workflowai.adapter.in.rest.dto.ErrorPayload;
import io.workflowai.adapter.in.rest.dto.EventType;
import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.domain.conversation.ConversationMessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;
import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Sql
class ChatEndpointTest extends IntegrationBase {

    // agent is defined via ChatEndpointTest.sql
    private final UUID AGENT_ID = UUID.fromString("1a907243-9428-41e3-a3d1-2c25ffd2a14f");
    private static final String GREET_JSON = """
            {"decisionMode":"GREET","detectedTopics":[],"extractedIntent":"Hello","clarificationQuestion":null,"reason":"Greeting"}
            """;
    private static final String EXECUTE_JSON = """
            {"decisionMode":"EXECUTE","detectedTopics":[],"extractedIntent":"Deploy the latest release","clarificationQuestion":null,"reason":"User wants to execute a task"}
            """;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private ConversationMessageStorage conversationMessageStorage;

    @MockitoBean
    private ChatProviderRegistry chatProviderRegistry;

    @BeforeEach
    void setUp() {
        ChatProvider ollama = mock();
        when(ollama.getId()).thenReturn(Ollama);
        when(ollama.stream(any(ChatCompletionRequest.class), any(Consumer.class))).thenReturn("Test response");
        when(ollama.call(any(ChatCompletionRequest.class))).thenReturn(GREET_JSON);

        when(chatProviderRegistry.get(Ollama)).thenReturn(ollama);
        when(chatProviderRegistry.supportedChatProviders()).thenReturn(Map.of(Ollama, Set.of()));
    }

    @Test
    void chat_persistsExactlyOneUserAndOneAgentMessagePerTurn() {
        ConversationResponse conversation = newConversation(AGENT_ID);

        List<ConversationMessage> messages =
                conversationMessageStorage.findByAgentIdAndConversationId(conversation.agentId(), conversation.id());

        long userMessages = messages.stream().filter(m -> m.role() == ConversationMessageRole.USER).count();
        long agentMessages = messages.stream().filter(m -> m.role() == ConversationMessageRole.AGENT).count();

        assertThat(userMessages).isEqualTo(1);
        assertThat(agentMessages).isEqualTo(1);
    }

    @Test
    void chat_workflowExecutionFailure_sendsErrorEventAndCompletesStream() {
        ChatProvider ollama = mock();
        when(ollama.getId()).thenReturn(Ollama);
        when(ollama.call(any(ChatCompletionRequest.class))).thenReturn(EXECUTE_JSON);
        when(ollama.stream(any(ChatCompletionRequest.class), any(Consumer.class)))
                .thenThrow(new RuntimeException("Simulated provider outage"));
        when(chatProviderRegistry.get(Ollama)).thenReturn(ollama);
        when(chatProviderRegistry.supportedChatProviders()).thenReturn(Map.of(Ollama, Set.of()));

        String rawStream = given()
                .contentType(ContentType.JSON)
                .body("{\"message\":\"Deploy the latest release\"}")
                .when()
                .post("/api/agents/{agentId}/conversations/{id}/chat", AGENT_ID, "NEW_CONVERSATION")
                .then()
                .extract()
                .asString();

        ErrorPayload error = jsonMapper.readValue(extractEventData(EventType.ERROR, rawStream), ErrorPayload.class);
        assertThat(error.message()).contains("Simulated provider outage");
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
    void getWorkflowDiagram_rendersMermaidDiagram() {
        given()
                .when()
                .get("/api/admin/agents/{agentId}/workflowDiagram", AGENT_ID)
                .then()
                .statusCode(200)
                .body(containsString("flowchart"));
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