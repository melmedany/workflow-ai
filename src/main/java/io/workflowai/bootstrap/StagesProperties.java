package io.workflowai.bootstrap;

import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.workflow.StageId;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "workflow-ai.stages")
public record StagesProperties(List<StageProperties> stages) {

    public StageProperties get(StageId stageId) {
        return stages.stream()
                .filter(stage -> stage.stageId() == stageId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No stage properties configured for stage " + stageId));
    }

    public record StageProperties(StageId stageId, ChatProviderId chatProviderId, String model, double temperature) {
    }
}

