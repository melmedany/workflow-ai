package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.WorkflowState;
import io.workflowai.domain.workflow.StageId;

import java.util.Map;

public interface WorkflowStage {

    StageId stageId();

    Map<String, Object> execute(WorkflowState state);
}