package io.workflowai.adapter.in.rest;

import io.workflowai.adapter.in.rest.dto.AgentMapper;
import io.workflowai.adapter.in.rest.dto.AgentSummaryDto;
import io.workflowai.application.port.in.AgentDefinitionUseCase;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.ChatProviderId;
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
public class AgentDefinitionController {

    private final AgentDefinitionUseCase agentDefinitionUseCase;

    public AgentDefinitionController(AgentDefinitionUseCase agentDefinitionUseCase) {
        this.agentDefinitionUseCase = agentDefinitionUseCase;
    }

    @GetMapping("/supported-chat-providers")
    public ResponseEntity<Map<ChatProviderId, Set<String>>> supportedChatProviders() {
        return ResponseEntity.ok(agentDefinitionUseCase.supportedChatProviders());
    }

    @GetMapping({"", "/"})
    public ResponseEntity<List<AgentSummaryDto>> getAgents() {
        return ResponseEntity.ok(agentDefinitionUseCase.getAllDefinitions().stream()
                .map(AgentMapper::toAgentSummary)
                .toList());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> getAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentDefinitionUseCase.getDefinition(agentId));
    }

    @GetMapping(path = "/{agentId}/workflowDiagram", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getWorkflowDiagram(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentDefinitionUseCase.workflowDiagram(agentId));
    }

    @PostMapping({"", "/"})
    public ResponseEntity<AgentDefinition> createAgent(@RequestBody AgentDefinition definition) {
        AgentDefinition created = agentDefinitionUseCase.saveDefinition(definition);
        return ResponseEntity.created(URI.create("/api/admin/agents/%s".formatted(created.agentId()))).body(created);
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> updateAgent(@PathVariable UUID agentId, @RequestBody AgentDefinition definition) {
        AgentDefinition updated = agentDefinitionUseCase.updateDefinition(new AgentDefinition(
                agentId,
                definition.details(),
                definition.workflowId(),
                definition.chatProperties(),
                definition.workflowPolicy()));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteAgent(@PathVariable UUID agentId) {
        agentDefinitionUseCase.deleteDefinition(agentId);
        return ResponseEntity.noContent().build();
    }
}