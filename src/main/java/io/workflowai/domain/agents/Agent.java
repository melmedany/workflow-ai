package io.workflowai.domain.agents;

import io.workflowai.domain.model.AgentProperties;
import io.workflowai.domain.model.AgentRequest;

import java.util.List;
import java.util.UUID;

public interface Agent {
    AgentProperties properties();

    List<String> tags();

    String workflowDiagram();

    void execute(UUID runId, AgentRequest request);
}
