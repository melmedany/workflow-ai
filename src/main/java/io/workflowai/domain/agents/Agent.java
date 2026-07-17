package io.workflowai.domain.agents;

import io.workflowai.domain.model.AgentConfig;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.workflow.PipelineEvent;

import java.util.List;
import java.util.function.Consumer;

public interface Agent {
    AgentConfig getConfig();

    List<String> tags();

    void execute(AgentRequest request, Consumer<PipelineEvent> eventConsumer);
}
