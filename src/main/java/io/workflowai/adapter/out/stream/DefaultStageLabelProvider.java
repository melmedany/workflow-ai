package io.workflowai.adapter.out.stream;

import io.workflowai.domain.workflow.StageId;

import java.util.Map;

/**
 * Default label provider for stages to UI. Should be i18n as per request language.
 */
public class DefaultStageLabelProvider {

    private static final Map<StageId, String> LABELS = Map.ofEntries(
            Map.entry(StageId.GUARDRAIL_INPUT, "Checking request"),
            Map.entry(StageId.PERSIST_USER_MESSAGE, "Request saved"),
            Map.entry(StageId.LOAD_MEMORY, "Context prepared"),
            Map.entry(StageId.CLASSIFICATION, "Request classifying"),
            Map.entry(StageId.EXECUTE_WORKFLOW, "Generating response"),
            Map.entry(StageId.GENERATE_CLARIFICATION, "Preparing clarification"),
            Map.entry(StageId.GENERATE_GREETING, "Preparing greeting"),
            Map.entry(StageId.GENERATE_REDIRECT, "Preparing redirect"),
            Map.entry(StageId.GENERATE_REFUSAL, "Preparing refusal"),
            Map.entry(StageId.GUARDRAIL_OUTPUT, "Checking response"),
            Map.entry(StageId.SELF_VERIFICATION, "Verifying output"),
            Map.entry(StageId.PERSIST_RESPONSE, "Saving response"),
            Map.entry(StageId.COMPACT_MEMORY, "Updating memory"),
            Map.entry(StageId.COMPLETE, "Completed")
    );

    public String started(StageId stageId) {
        return LABELS.getOrDefault(stageId, stageId.name());
    }

    public String completed(StageId stageId) {
        return LABELS.getOrDefault(stageId, stageId.name());
    }

    public String failed(StageId stageId) {
        return "%s failed".formatted(LABELS.getOrDefault(stageId, stageId.name()));
    }
}
