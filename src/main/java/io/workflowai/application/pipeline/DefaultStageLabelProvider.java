package io.workflowai.application.pipeline;

import io.workflowai.domain.workflow.StageId;

import java.util.Map;

public class DefaultStageLabelProvider implements StageLabelProvider {

    private static final Map<StageId, String> LABELS = Map.ofEntries(
            Map.entry(StageId.PERSIST_USER_MESSAGE, "Request saved"),
            Map.entry(StageId.LOAD_MEMORY, "Context prepared"),
            Map.entry(StageId.CLASSIFICATION, "Request classifying"),
            Map.entry(StageId.ROUTING, "Decision made"),
            Map.entry(StageId.EXECUTE_WORKFLOW, "Generating response"),
            Map.entry(StageId.GENERATE_CLARIFICATION, "Preparing clarification"),
            Map.entry(StageId.GENERATE_REDIRECT, "Preparing redirect"),
            Map.entry(StageId.APPLY_REFUSE, "Preparing refusal"),
            Map.entry(StageId.SELF_VERIFICATION, "Verifying output"),
            Map.entry(StageId.PERSIST_RESPONSE, "Saving response"),
            Map.entry(StageId.PERSIST_MEMORY, "Updating memory"),
            Map.entry(StageId.COMPLETE, "Completed")
    );

    @Override
    public String started(StageId stageId) {
        return LABELS.getOrDefault(stageId, stageId.name());
    }

    @Override
    public String completed(StageId stageId) {
        return LABELS.getOrDefault(stageId, stageId.name());
    }

    @Override
    public String failed(StageId stageId) {
        return "%s failed".formatted(LABELS.getOrDefault(stageId, stageId.name()));
    }
}
