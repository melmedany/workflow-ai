package io.workflowai.adapters.inbound.rest;

import io.workflowai.application.AgentService;
import io.workflowai.application.LLMProviderId;
import io.workflowai.domain.agents.AgentDefinition;
import io.workflowai.ports.inbound.AgentAdminManager;
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

    private final AgentAdminManager agentAdminManager;
    private final AgentService agentService;

    public AgentAdminController(AgentAdminManager agentAdminManager, AgentService agentService) {
        this.agentAdminManager = agentAdminManager;
        this.agentService = agentService;
    }

    @GetMapping("/supported-llm-providers")
    public ResponseEntity<Map<LLMProviderId, Set<String>>> supportedLLmProviders() {
        return ResponseEntity.ok(agentAdminManager.supportedLLMProviders());
    }

    @GetMapping({"", "/"})
    public ResponseEntity<List<AgentDefinition>> getAgents() {
        return ResponseEntity.ok(agentAdminManager.getAllDefinitions());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> getAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentAdminManager.getDefinition(agentId));
    }

    @GetMapping(path = "/{agentId}/workflowDiagram", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getWorkflowDiagram(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentService.get(agentId).workflowDiagram());
    }

    @PostMapping({"", "/"})
    public ResponseEntity<AgentDefinition> createAgent(@RequestBody AgentDefinition definition) {
        AgentDefinition created = agentAdminManager.saveDefinition(definition);
        return ResponseEntity.created(URI.create("/api/admin/agents/%s".formatted(created.agentId()))).body(created);
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> updateAgent(@PathVariable UUID agentId, @RequestBody AgentDefinition definition) {
        AgentDefinition updated = agentAdminManager.updateDefinition(new AgentDefinition(
                agentId,
                definition.details(),
                definition.llmProperties(),
                definition.workflowPolicyProperties()));

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteAgent(@PathVariable UUID agentId) {
        agentAdminManager.deleteDefinition(agentId);
        return ResponseEntity.noContent().build();
    }
}