package io.workflowai.application.pipeline;

import io.workflowai.domain.model.RoutingDecision;
import io.workflowai.domain.model.TriggerSource;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WorkflowContext extends AgentState {

    public static final AgentStateFactory<WorkflowContext> SCHEMA = WorkflowContext::new;

    protected static final String KEY_RUN_ID = "runId";
    protected static final String KEY_CONVERSATION_ID = "conversationId";
    protected static final String KEY_USER_MESSAGE = "userMessage";
    protected static final String KEY_TRIGGER_SOURCE = "triggerSource";
    protected static final String KEY_MEMORY_CONTEXT = "memoryContext";
    protected static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    protected static final String KEY_ROUTING_DECISION = "routingDecision";
    protected static final String KEY_GENERATED_RESPONSE = "generatedResponse";
    protected static final String KEY_VALIDATION_PASSED = "validationPassed";
    protected static final String KEY_VALIDATION_FAILURE_REASON = "validationFailureReason";
    protected static final String KEY_RETRIED = "retried";
    protected static final String KEY_GUARDRAIL_BLOCKED = "guardrailBlocked";

    public WorkflowContext(Map<String, Object> initData) {
        super(initData);
    }

    public UUID runId() {
        return this.<UUID>value(KEY_RUN_ID)
                .orElseThrow(() -> new IllegalStateException("runID not set in workflow context"));
    }

    public UUID conversationId() {
        return this.<UUID>value(KEY_CONVERSATION_ID)
                .orElseThrow(() -> new IllegalStateException("conversationID not set in workflow context"));
    }

    public String userMessage() {
        return this.<String>value(KEY_USER_MESSAGE)
                .orElseThrow(() -> new IllegalStateException("userMessage not set in workflow context"));
    }

    public TriggerSource triggerSource() {
        return this.<TriggerSource>value(KEY_TRIGGER_SOURCE).orElse(TriggerSource.USER_MESSAGE);
    }

    public String memoryContext() {
        return this.<String>value(KEY_MEMORY_CONTEXT).orElse("");
    }

    public String systemPrompt() {
        return this.<String>value(KEY_SYSTEM_PROMPT).orElse("");
    }

    public Optional<RoutingDecision> routingDecision() {
        return value(KEY_ROUTING_DECISION);
    }

    public Optional<String> generatedResponse() {
        return value(KEY_GENERATED_RESPONSE);
    }

    public boolean validationPassed() {
        return this.<Boolean>value(KEY_VALIDATION_PASSED).orElse(false);
    }

    public String validationFailureReason() {
        return this.<String>value(KEY_VALIDATION_FAILURE_REASON).orElse("unspecified quality issue");
    }

    public boolean retried() {
        return this.<Boolean>value(KEY_RETRIED).orElse(false);
    }

    public boolean guardrailBlocked() {
        return this.<Boolean>value(KEY_GUARDRAIL_BLOCKED).orElse(false);
    }

    public boolean guardrailPassed() {
        return !this.<Boolean>value(KEY_GUARDRAIL_BLOCKED).orElse(false);
    }
}