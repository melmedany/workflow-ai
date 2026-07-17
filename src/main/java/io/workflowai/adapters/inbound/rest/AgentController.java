package io.workflowai.adapters.inbound.rest;

import io.workflowai.adapters.inbound.rest.dto.AgentInfo;
import io.workflowai.adapters.inbound.rest.dto.ChatRequest;
import io.workflowai.adapters.inbound.rest.dto.ConversationResponse;
import io.workflowai.adapters.inbound.rest.dto.DecisionPayload;
import io.workflowai.adapters.inbound.rest.dto.ErrorPayload;
import io.workflowai.adapters.inbound.rest.dto.Mappers;
import io.workflowai.adapters.inbound.rest.dto.MessageResponse;
import io.workflowai.adapters.inbound.rest.dto.StagePayload;
import io.workflowai.application.ConversationService;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.workflow.PipelineEvent;
import io.workflowai.ports.inbound.AgentPort;
import io.workflowai.ports.inbound.ConversationPort;
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

import static io.workflowai.adapters.inbound.rest.dto.Mappers.toAgentInfo;
import static io.workflowai.adapters.inbound.rest.dto.Mappers.toConversationResponse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentPort agentService;
    private final ConversationPort conversationService;
    private final JsonMapper jsonMapper;

    public AgentController(
            AgentPort agentService,
            ConversationService conversationService,
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

    @GetMapping("/{agentId}/reload")
    public ResponseEntity<Void> reloadAgent(@PathVariable UUID agentId) {
        agentService.reload(agentId);
        return ResponseEntity.ok().build();
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
    public void deleteConversation(@PathVariable UUID agentId, @PathVariable UUID conversationId) {
        conversationService.deleteConversation(agentId, conversationId);
        log.info("Conversation [{}] deleted", conversationId);
    }

    @GetMapping("/{agentId}/conversations/{conversationId}/messages")
    public List<MessageResponse> getMessages(@PathVariable UUID agentId, @PathVariable UUID conversationId) {
        return conversationService.getMessages(agentId, conversationId).stream()
                .map(Mappers::toMessageResponse)
                .toList();
    }

    @PostMapping(value = "/{agentId}/conversations/{convId}/chat", produces = TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable UUID agentId, @PathVariable String convId, @RequestBody ChatRequest request) {
        boolean newConversation = "NEW_CONVERSATION".equalsIgnoreCase(convId);
        UUID conversationId = resolveConversationId(convId, agentId, request);
        ConversationResponse conversation = toConversationResponse(conversationService.getConversation(agentId, conversationId));

        SseEmitter emitter = new SseEmitter(120_000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> {
            log.warn("SSE connection error for conversation [{}]: {}", conversationId, e.getMessage());
            emitter.complete();
        });

        Thread.startVirtualThread(() -> {
            try {
                if (newConversation) {
                    sendJson(emitter, "conversation_created", conversation);
                }

                agentService
                        .get(conversation.agentId())
                        .execute(new AgentRequest(request.message(), conversationId), event -> handleEvent(emitter, conversationId, event));
                emitter.complete();
            } catch (Exception e) {
                log.warn("Chat execution failed for conversation [{}]: {}", conversationId, e.getMessage());
                sendError(emitter, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private UUID resolveConversationId(String conversationId, UUID agentId, ChatRequest request) {
        return "NEW_CONVERSATION".equalsIgnoreCase(conversationId) ?
                conversationService.createConversation(agentId, request.message()).id() : UUID.fromString(conversationId);
    }

    // ── Event dispatch ───────────────────────────────────────────────────────

    private void handleEvent(SseEmitter emitter, UUID conversationId, PipelineEvent event) {
        switch (event) {
            case PipelineEvent.StageStarted e ->
                    sendJson(emitter, "stage", new StagePayload(e.stageId().name(), "STARTED", e.label(), null));
            case PipelineEvent.StageCompleted e ->
                    sendJson(emitter, "stage", new StagePayload(e.stageId().name(), "COMPLETED", e.label(), null));
            case PipelineEvent.StageFailed e ->
                    sendJson(emitter, "stage", new StagePayload(e.stageId().name(), "FAILED", e.label(), e.reason()));
            case PipelineEvent.DecisionMade e ->
                    sendJson(emitter, "decision", new DecisionPayload(e.mode().name(), e.reason()));
            case PipelineEvent.Token e -> sendText(emitter, "token", e.token());
            case PipelineEvent.ResponseCompleted ignored -> sendJson(emitter, "response_completed", null);
            case PipelineEvent.MemoryUpdated ignored -> sendJson(emitter, "memory_updated", null);
            case PipelineEvent.ConversationCompleted ignored -> sendJson(emitter, "conversation_completed", null);
            case PipelineEvent.Error e -> sendJson(emitter, "error", new ErrorPayload(e.message()));
        }
    }

    // ── SSE helpers ──────────────────────────────────────────────────────────

    private void sendJson(SseEmitter emitter, String eventName, Object payload) {
        try {
            String data = payload != null ? jsonMapper.writeValueAsString(payload) : "{}";
            emitter.send(SseEmitter.event().name(eventName).data(data, APPLICATION_JSON));
        } catch (IOException e) {
            log.warn("Failed to send SSE event [{}]: {}", eventName, e.getMessage());
            throw new RuntimeException("SSE write failed for event: " + eventName, e);
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event [{}]: {}", eventName, e.getMessage());
        }
    }

    private void sendText(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data, TEXT_PLAIN));
        } catch (IOException e) {
            log.warn("Failed to send SSE token: {}", e.getMessage());
            throw new RuntimeException("SSE token write failed", e);
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data("{\"message\":\"" + message + "\"}", APPLICATION_JSON));
        } catch (IOException ignored) {
            // emitter may already be closed
        }
    }
}