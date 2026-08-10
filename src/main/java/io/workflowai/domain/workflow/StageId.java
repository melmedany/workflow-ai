package io.workflowai.domain.workflow;

public enum StageId {
    GUARDRAIL_INPUT(false),
    PERSIST_USER_MESSAGE(false),
    LOAD_MEMORY(false),
    CLASSIFICATION(true),
    EXECUTE_WORKFLOW(true),
    CREATE_TASK(true),
    GENERATE_CLARIFICATION(true),
    GENERATE_GREETING(true),
    GENERATE_REDIRECT(true),
    GENERATE_REFUSAL(true),
    GUARDRAIL_OUTPUT(false),
    SELF_VERIFICATION(true),
    PERSIST_RESPONSE(false),
    COMPACT_MEMORY(false),
    COMPLETE(true);

    private final boolean agentFacing;

    StageId(boolean agentFacing) {
        this.agentFacing = agentFacing;
    }

    public boolean isAgentFacing() {
        return agentFacing;
    }
}
