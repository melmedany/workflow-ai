package io.workflowai.adapters.inbound.rest;

import io.workflowai.domain.model.AgentDefinition;
import io.workflowai.domain.model.ProviderOption;
import io.workflowai.ports.inbound.AgentAdminPort;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/agents")
public class AgentAdminController {

    private final AgentAdminPort agentAdminPort;

    public AgentAdminController(AgentAdminPort agentAdminPort) {
        this.agentAdminPort = agentAdminPort;
    }

    @GetMapping("/providers")
    public List<ProviderOption> supportedProviders() {
        return agentAdminPort.supportedProviders();
    }

    @GetMapping({"", "/"})
    public ResponseEntity<List<AgentDefinition>> getAgents() {
        return ResponseEntity.ok(agentAdminPort.getAllDefinitions());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> getAgent(@PathVariable UUID agentId) {
        return ResponseEntity.ok(agentAdminPort.getDefinition(agentId));
    }

    @PostMapping
    public ResponseEntity<AgentDefinition> createAgent(@RequestBody AgentDefinition definition) {
        AgentDefinition created = agentAdminPort.saveDefinition(definition);
        return ResponseEntity.created(URI.create("/api/admin/agents/%s".formatted(created.agentId()))).body(created);
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<AgentDefinition> updateAgent(@PathVariable UUID agentId, @RequestBody AgentDefinition definition) {
        AgentDefinition updated = agentAdminPort.updateDefinition(new AgentDefinition(
                agentId,
                definition.details(),
                definition.llmConfig(),
                definition.policyConfig()));

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteAgent(@PathVariable UUID agentId) {
        agentAdminPort.deleteDefinition(agentId);
        return ResponseEntity.noContent().build();
    }
}