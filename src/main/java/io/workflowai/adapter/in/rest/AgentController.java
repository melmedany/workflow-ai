package io.workflowai.adapter.in.rest;

import io.workflowai.adapter.in.rest.dto.AgentInfo;
import io.workflowai.adapter.in.rest.dto.ChatRequest;
import io.workflowai.adapter.in.rest.dto.ConversationResponse;
import io.workflowai.adapter.in.rest.dto.DecisionPayload;
import io.workflowai.adapter.in.rest.dto.ErrorPayload;
import io.workflowai.adapter.in.rest.dto.EventType;
import io.workflowai.adapter.in.rest.dto.Mappers;
import io.workflowai.adapter.in.rest.dto.MessageResponse;
import io.workflowai.adapter.in.rest.dto.StagePayload;
import io.workflowai.application.execution.AgentRequest;
import io.workflowai.domain.workflow.WorkflowEvent;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.port.in.AgentProvider;
import io.workflowai.application.port.in.ConversationProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static io.workflowai.adapter.in.rest.dto.EventType.CONVERSATION_COMPLETED;
import static io.workflowai.adapter.in.rest.dto.EventType.CONVERSATION_CREATED;
import static io.workflowai.adapter.in.rest.dto.EventType.DECISION;
import static io.workflowai.adapter.in.rest.dto.EventType.ERROR;
import static io.workflowai.adapter.in.rest.dto.EventType.MEMORY_UPDATED;
import static io.workflowai.adapter.in.rest.dto.EventType.RESPONSE_COMPLETED;
import static io.workflowai.adapter.in.rest.dto.EventType.STAGE;
import static io.workflowai.adapter.in.rest.dto.EventType.TOKEN;
import static io.workflowai.adapter.in.rest.dto.Mappers.toAgentInfo;
import static io.workflowai.adapter.in.rest.dto.Mappers.toConversationResponse;
import static io.workflowai.adapter.in.rest.dto.StagePayload.StageStatus.COMPLETED;
import static io.workflowai.adapter.in.rest.dto.StagePayload.StageStatus.FAILED;
import static io.workflowai.adapter.in.rest.dto.StagePayload.StageStatus.STARTED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentProvider agentService;
    private final ConversationProvider conversationService;
    private final JsonMapper jsonMapper;

    public AgentController(
            AgentProvider agentService,
            ConversationProvider conversationService,
            JsonMapper jsonMapper) {
        this.agentService = agentService;
        this.conversationService = conversationService;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping({"", "/"})
    public ResponseEntity<List<AgentInfo>> getAgents() {
        List<AgentInfo> agents = agentService.getAll().stream()
                .map(Mappers::toAgentInfo)
                .toList();
        return ResponseEntity.ok(agents);
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentInfo> getAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(toAgentInfo(agentService.get(agentId)));
    }

    @GetMapping("/{agentId}/conversations")
    public ResponseEntity<List<ConversationResponse>> getConversations(@PathVariable UUID agentId) {
        List<ConversationResponse> conversations = conversationService.getConversationsForAgent(agentId).stream()
                .map(Mappers::toConversationResponse)
                .toList();
        return ResponseEntity.ok(conversations);
    }

    @DeleteMapping("/{agentId}/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID agentId, @PathVariable UUID conversationId) {
        conversationService.deleteConversation(agentId, conversationId);
        log.debug("Conversation [{}] deleted", conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{agentId}/conversations/{conversationId}/messages")
    public ResponseEntity<List<MessageResponse>> getMessages(@PathVariable UUID agentId, @PathVariable UUID conversationId) {
        return ResponseEntity.ok(conversationService.getMessages(agentId, conversationId).stream()
                .map(Mappers::toMessageResponse)
                .toList());
    }

    @PostMapping(value = "/{agentId}/conversations/{convId}/chat", produces = TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chat(@PathVariable UUID agentId, @PathVariable String convId, @RequestBody ChatRequest request) {
        boolean newConversation = "NEW_CONVERSATION".equalsIgnoreCase(convId);
        UUID conversationId = resolveConversationId(convId, agentId, request);
        ConversationResponse conversation = toConversationResponse(conversationService.getConversation(agentId, conversationId));

        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(ex -> {
            log.warn("SSE connection error for conversation [{}]: {}", conversationId, ex.getMessage());
            emitter.completeWithError(ex);
        });

        Thread.startVirtualThread(() -> {
            try {
                if (newConversation) {
                    sendJson(emitter, CONVERSATION_CREATED, conversation);
                }

                agentService.trigger(
                        AgentRequest.userMessage(agentId, conversationId, request.message()),
                        event -> handleEvent(emitter, event)
                );
                emitter.complete();
            } catch (Exception ex) {
                log.warn("Chat execution failed for conversation [{}]: {}", conversationId, ex.getMessage());
                sendError(emitter, ex.getMessage());
                emitter.completeWithError(ex);
            }
        });

        return ResponseEntity.ok(emitter);
    }

    private UUID resolveConversationId(String conversationId, UUID agentId, ChatRequest request) {
        return "NEW_CONVERSATION".equalsIgnoreCase(conversationId) ?
                conversationService.createConversation(agentId, request.message()).id() : UUID.fromString(conversationId);
    }

    // ── Event dispatch ───────────────────────────────────────────────────────

    private void handleEvent(SseEmitter emitter, WorkflowEvent event) {
        switch (event) {
            case WorkflowEvent.StageStarted e ->
                    sendStage(emitter, e.stageId(), e.stageId().isAgentFacing(), STARTED, e.label(), null);
            case WorkflowEvent.StageCompleted e ->
                    sendStage(emitter, e.stageId(), e.stageId().isAgentFacing(), COMPLETED, e.label(), null);
            case WorkflowEvent.StageFailed e ->
                    sendStage(emitter, e.stageId(), e.stageId().isAgentFacing(), FAILED, e.label(), e.reason());
            case WorkflowEvent.DecisionMade e ->
                    sendJson(emitter, DECISION, new DecisionPayload(e.mode().name(), e.reason()));
            case WorkflowEvent.Token e -> sendText(emitter, TOKEN, e.token());
            case WorkflowEvent.ResponseCompleted ignored -> sendJson(emitter, RESPONSE_COMPLETED, null);
            case WorkflowEvent.MemoryUpdated ignored -> sendJson(emitter, MEMORY_UPDATED, null);
            case WorkflowEvent.ConversationCompleted ignored -> sendJson(emitter, CONVERSATION_COMPLETED, null);
            case WorkflowEvent.Error e -> sendJson(emitter, ERROR, new ErrorPayload(e.message()));
        }
    }

    // ── SSE helpers ──────────────────────────────────────────────────────────

    private void sendStage(SseEmitter emitter, StageId stageId, boolean agentFacing, StagePayload.StageStatus status, String label, String reason) {
        if (agentFacing) {
            sendJson(emitter, STAGE, new StagePayload(stageId, status, label, reason));
        }
    }

    private void sendJson(SseEmitter emitter, EventType eventType, Object payload) {
        try {
            String data = payload != null ? jsonMapper.writeValueAsString(payload) : "{}";
            emitter.send(SseEmitter.event().name(eventType.name()).data(data, APPLICATION_JSON));
        } catch (IOException ex) {
            log.warn("Failed to send SSE event [{}]: {}", eventType, ex.getMessage());
            throw new RuntimeException("SSE write failed for event: " + eventType, ex);
        } catch (Exception ex) {
            log.warn("Failed to serialize SSE event [{}]: {}", eventType, ex.getMessage());
        }
    }

    private void sendText(SseEmitter emitter, EventType eventType, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventType.name()).data(data, TEXT_PLAIN));
        } catch (IOException ex) {
            log.warn("Failed to send SSE token: {}", ex.getMessage());
            throw new RuntimeException("SSE token write failed", ex);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        sendJson(emitter, ERROR, new ErrorPayload(message));
    }
}