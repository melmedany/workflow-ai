package io.workflowai.application.execution;

import io.workflowai.domain.agent.AgentProperties;

import java.util.List;
import java.util.UUID;

public interface Agent {
    AgentProperties properties();

    List<String> tags();

    String workflowDiagram();

    void execute(UUID runId, AgentRequest request);
}
