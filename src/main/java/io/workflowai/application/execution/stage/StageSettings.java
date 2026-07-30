package io.workflowai.application.execution.stage;

import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.workflow.StageId;

import java.util.List;

public record StageSettings(List<StageSettings.StageSetting> stages) {

    public StageSetting get(StageId stageId) {
        return stages.stream()
                .filter(stage -> stage.stageId() == stageId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No stage properties configured for stage " + stageId));
    }

    public record StageSetting(StageId stageId, ChatProviderId chatProviderId, String model, double temperature) {
    }
}
