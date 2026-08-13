package io.workflowai.application.port.in;

import io.workflowai.application.execution.Agent;
import io.workflowai.application.execution.AgentRequest;
import io.workflowai.domain.workflow.WorkflowEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface AgentUseCase {

    UUID trigger(AgentRequest request, Consumer<WorkflowEvent> eventConsumer);

    Agent getEnabledAgent(UUID agentId);

    List<Agent> getEnabledAgents();

    void reload(UUID agentId);

    void remove(UUID agentId);

    String workflowDiagram(UUID agentId);
}
