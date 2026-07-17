package io.workflowai.integration;

import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class AgentAdminEndpointTest extends IntegrationBase {

    @Test
    @Transactional
    void saveAgent() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "agentId": null,
                          "details": {
                            "displayName": "Agent 1",
                            "description": "test agent",
                            "enabled": true
                          },
                          "llmConfig": {
                            "provider": "ollama",
                            "model": "deepseek-r1:7b",
                            "temperature": 0.4,
                            "memoryEnabled": true,
                            "validationEnabled": true,
                            "memoryLimit": 7
                          },
                          "policyConfig": {
                            "capabilities": ["run tests", "verify criteria"],
                            "greetings": ["Hi"],
                            "refuseMessages": ["I can't do that"],
                            "redirectMessages": ["I can do that , but I need more details"],
                            "maxRetries": 2
                          }
                        }
                        """)
                .when()
                .post("/api/admin/agents")
                .then()
                .statusCode(201)
                .body("agentId", is(notNullValue()))
                .body("details.displayName", containsString("Agent 1"));
    }

    @Test
    @Transactional
    void updateAgent() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "agentId": "6ca207fa-30be-43f0-b4b3-a7e2a1ea650e",
                          "details": {
                            "displayName": "Updated agent name",
                            "description": "test agent",
                            "enabled": true
                          },
                          "llmConfig": {
                            "provider": "ollama",
                            "model": "deepseek-r1:7b",
                            "temperature": 0.4,
                            "memoryEnabled": true,
                            "validationEnabled": true,
                            "memoryLimit": 7
                          },
                          "policyConfig": {
                            "capabilities": ["run tests", "verify criteria"],
                            "greetings": ["Hi"],
                            "refuseMessages": ["I can't do that"],
                            "redirectMessages": ["I can do that , but I need more details"],
                            "maxRetries": 2
                          }
                        }
                        """)
                .when()
                .put("/api/admin/agents/{agentId}", "6ca207fa-30be-43f0-b4b3-a7e2a1ea650e")
                .then()
                .statusCode(200)
                .body("details.displayName", containsString("Updated agent name"));
    }
}