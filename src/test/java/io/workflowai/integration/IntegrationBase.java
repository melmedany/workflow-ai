package io.workflowai.integration;

import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.workflowai.bootstrap.WorkflowAIApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = WorkflowAIApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationBase {

    @LocalServerPort
    private int port;

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withUsername("workflow-ai")
            .withPassword("workflow-ai")
            .withDatabaseName("workflow-ai")
            .withReuse(Boolean.TRUE)
            .waitingFor(Wait.forListeningPort());


    @BeforeEach
    void setup() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.registerParser("text/event-stream", Parser.JSON);
    }
}