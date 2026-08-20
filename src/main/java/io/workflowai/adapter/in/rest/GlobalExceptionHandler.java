package io.workflowai.adapter.in.rest;

import io.workflowai.domain.exceptions.AgentNotFoundException;
import io.workflowai.domain.exceptions.AgentValidationException;
import io.workflowai.domain.exceptions.ChatProviderException;
import io.workflowai.domain.exceptions.ConversationNotFoundException;
import io.workflowai.domain.exceptions.TaskNotFoundException;
import io.workflowai.domain.exceptions.WorkflowExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTaskNotFoundException(
            TaskNotFoundException ex, WebRequest request) {
        log.debug("Task not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ChatProviderException.class)
    public ResponseEntity<Map<String, Object>> handleChatProviderException(
            ChatProviderException ex, WebRequest request) {
        log.warn("Chat provider error: {}", ex.getMessage());
        return error(HttpStatus.BAD_GATEWAY, "Chat provider error: " + ex.getMessage(), request);
    }

    @ExceptionHandler(WorkflowExecutionException.class)
    public ResponseEntity<Map<String, Object>> handleWorkflowExecution(
            WorkflowExecutionException ex, WebRequest request) {
        log.error("Workflow execution error for agent: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Workflow execution failed", request);
    }

    @ExceptionHandler(AgentValidationException.class)
    public ResponseEntity<Map<String, Object>> handleAgentValidation(
            AgentValidationException ex, WebRequest request) {
        log.debug("Agent validation error: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        log.debug("Invalid argument error: {}", ex.getMessage(), ex);
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException ex, WebRequest request) {
        log.debug("Missing required request parameter", ex);
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex, WebRequest request) {
        log.debug("JSON parse error", ex);

        if (ex.getCause() instanceof MismatchedInputException mie) {
            String field = mie.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            Object rejectedValue = mie.getCurrentToken();
            log.error("JSON parse error on field: {}, rejected value: {}", field, rejectedValue);
            return error(HttpStatus.BAD_REQUEST,
                    "Invalid value for field '" + field + "': rejected value " + rejectedValue, request);
        }

        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest request) {
        // TODO handle `Unhandled exception [HttpMessageNotWritableException]: No converter for [class java.util.LinkedHashMap] with preset Content-Type 'text/event-stream'`
        log.error("Unhandled exception [{}]: ", ex.getClass().getSimpleName(), ex);
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
