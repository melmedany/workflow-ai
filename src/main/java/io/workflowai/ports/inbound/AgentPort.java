package io.workflowai.ports.inbound;

import io.workflowai.domain.agents.Agent;

import java.util.List;
import java.util.UUID;

public interface AgentPort {
    Agent get(UUID id);

    void reload(UUID id);

    List<Agent> getAll();
}
