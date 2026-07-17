package io.workflowai.adapters.inbound.rest;

import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.exceptions.ClassificationException;
import io.workflowai.domain.exceptions.ConversationNotFoundException;
import io.workflowai.domain.exceptions.LlmProviderException;
import io.workflowai.domain.exceptions.WorkflowExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleConversationNotFound(
            ConversationNotFoundException ex, WebRequest request) {
        log.debug("Conversation not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAgentNotFound(
            AgentNotFoundException ex, WebRequest request) {
        log.debug("Agent not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(LlmProviderException.class)
    public ResponseEntity<Map<String, Object>> handleLlmProvider(
            LlmProviderException ex, WebRequest request) {
        log.warn("LLM provider error: {}", ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "LLM provider error: " + ex.getMessage(), request);
    }

    @ExceptionHandler(ClassificationException.class)
    public ResponseEntity<Map<String, Object>> handleClassification(
            ClassificationException ex, WebRequest request) {
        log.warn("Classification error for agent [{}]: {}", ex.getAgent(), ex.getMessage());
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "Could not classify request: " + ex.getMessage(), request);
    }

    @ExceptionHandler(WorkflowExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleWorkflowExecution(
            WorkflowExecutionException ex, WebRequest request) {
        log.warn("Workflow execution error for agent: {}", ex.getMessage());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Workflow execution failed", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        log.debug("Illegal argument: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(
            Exception ex, WebRequest request) {
        log.warn("Unhandled exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String message, WebRequest request) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", request.getDescription(false).replace("uri=", "")
        ));
    }
}
