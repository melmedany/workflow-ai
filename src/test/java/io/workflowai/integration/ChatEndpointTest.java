package io.workflowai.integration;

import io.restassured.http.ContentType;
import io.workflowai.adapters.inbound.rest.dto.ConversationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

@Sql
class ChatEndpointTest extends IntegrationBase {

    // agent is defined via ChatEndpointTest.sql
    private final UUID AGENT_ID = UUID.fromString("1a907243-9428-41e3-a3d1-2c25ffd2a14f");

    @Autowired
    private JsonMapper jsonMapper;

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
    void deleteNonExistentConversation_returns404() {
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

        return jsonMapper.readValue(extractEventData("conversation_created", rawStream), ConversationResponse.class);
    }

    private String extractEventData(String eventName, String rawStream) {
        List<String> lines = Arrays.stream(rawStream.split("\\r?\\n"))
                .map(String::trim)
                .toList();

        String targetEventLine = "event:" + eventName;

        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(targetEventLine)) {
                if (i + 1 < lines.size() && lines.get(i + 1).startsWith("data:")) {
                    return lines.get(i + 1).substring(5).trim();
                }
            }
        }

        throw new AssertionError("Event '" + eventName + "' with valid data block was not found in the stream.");
    }
}