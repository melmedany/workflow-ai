package io.workflowai.application;

import io.workflowai.domain.workflow.StageId;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "workflow-ai.stages")
public record StagesProperties(List<StageProperties> stages) {

    public record StageProperties(StageId stageId, LLMProviderId llmProviderId, String model, double temperature) {
    }
}

