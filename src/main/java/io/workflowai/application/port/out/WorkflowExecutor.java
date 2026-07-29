package io.workflowai.application.port.out;

import io.workflowai.domain.run.WorkflowExecutionResult;

import java.util.Map;

public interface WorkflowExecutor {

    WorkflowExecutionResult execute(Map<String, Object> initialState);

    String diagram(String title);
}