package io.workflowai.application.port.in;

import io.workflowai.application.execution.Agent;
import io.workflowai.application.execution.AgentRequest;
import io.workflowai.domain.workflow.WorkflowEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface AgentUseCase {

    Agent get(UUID id);

    void trigger(AgentRequest request, Consumer<WorkflowEvent> eventConsumer);

    List<Agent> getAll();

    String workflowDiagram(UUID id);
}
