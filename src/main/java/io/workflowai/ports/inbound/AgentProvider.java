package io.workflowai.ports.inbound;

import io.workflowai.domain.agents.Agent;
import io.workflowai.domain.model.AgentRequest;
import io.workflowai.domain.workflow.PipelineEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface AgentProvider {

    Agent get(UUID id);

    void trigger(AgentRequest request, Consumer<PipelineEvent> eventConsumer);

    List<Agent> getAll();
}
