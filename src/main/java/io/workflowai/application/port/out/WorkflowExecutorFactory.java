package io.workflowai.application.port.out;

import io.workflowai.domain.workflow.WorkflowId;

/**
 * Builds a {@link WorkflowExecutor} for a given workflow topology. Implemented by the LangGraph4j
 * runtime adapter; {@code WorkflowFactory} only ever sees this interface.
 */
public interface WorkflowExecutorFactory {

    WorkflowExecutor build(WorkflowId workflowId);
}