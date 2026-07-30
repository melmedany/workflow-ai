package io.workflowai.domain.workflow;

import java.util.Map;

public interface WorkflowStage {

    StageId stageId();

    Map<String, Object> execute(WorkflowState state);
}