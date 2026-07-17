package io.workflowai.application.pipeline;

import io.workflowai.domain.workflow.StageId;

public interface StageLabelProvider {

    String started(StageId stageId);

    String completed(StageId stageId);

    String failed(StageId stageId);
}
