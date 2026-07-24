package io.workflowai.domain.agents;

import io.workflowai.domain.model.AgentProperties;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.workflow.PipelineEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface Agent {
    AgentProperties properties();

    List<String> tags();

    String workflowDiagram();

    void execute(UUID runId, AgentRequest request, Consumer<PipelineEvent> eventConsumer);
}
