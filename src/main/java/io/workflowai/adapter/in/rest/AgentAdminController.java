package io.workflowai.adapter.in.rest;

import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.application.port.in.AgentAdminUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/agents")
public class AgentAdminController {

    private final AgentAdminUseCase agentAdminUseCase;
    private final AgentUseCase agentUseCase;

    public AgentAdminController(AgentAdminUseCase agentAdminUseCase, AgentUseCase agentUseCase) {
        this.agentAdminUseCase = agentAdminUseCase;
        this.agentUseCase = agentUseCase;
    }

    @GetMapping("/supported-chat-providers")
    public ResponseEntity<Map<ChatProviderId, Set<String>>> supportedChatProviders() {
        return ResponseEntity.ok(agentAdminUseCase.supportedChatProviders());
    }

    @GetMapping({"", "/"})
    public ResponseEntity<List<AgentDefinition>> getAgents() {
        return ResponseEntity.ok(agentAdminUseCase.getAllDefinitions());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> getAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentAdminUseCase.getDefinition(agentId));
    }

    @GetMapping(path = "/{agentId}/workflowDiagram", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getWorkflowDiagram(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentUseCase.workflowDiagram(agentId));
    }

    @PostMapping({"", "/"})
    public ResponseEntity<AgentDefinition> createAgent(@RequestBody AgentDefinition definition) {
        AgentDefinition created = agentAdminUseCase.saveDefinition(definition);
        return ResponseEntity.created(URI.create("/api/admin/agents/%s".formatted(created.agentId()))).body(created);
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> updateAgent(@PathVariable UUID agentId, @RequestBody AgentDefinition definition) {
        AgentDefinition updated = agentAdminUseCase.updateDefinition(new AgentDefinition(
                agentId,
                definition.details(),
                definition.chatProperties(),
                definition.workflowPolicyProperties()));

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteAgent(@PathVariable UUID agentId) {
        agentAdminUseCase.deleteDefinition(agentId);
        return ResponseEntity.noContent().build();
    }
}