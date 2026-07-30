package io.workflowai.domain.workflow;

import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.agent.TriggerSource;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plain, framework-free carrier for workflow state. Deliberately has no dependency on any graph
 * runtime: the LangGraph4j adapter translates its own state type into/out of this one at the
 * node boundary (see {@code adapters.outbound.runtime.langgraph4j}).
 */
public class WorkflowState extends AgentState {

    public static final String KEY_RUN_ID = "runId";
    public static final String KEY_CONVERSATION_ID = "conversationId";
    public static final String KEY_USER_MESSAGE = "userMessage";
    public static final String KEY_TRIGGER_SOURCE = "triggerSource";
    public static final String KEY_MEMORY_CONTEXT = "memoryContext";
    public static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    public static final String KEY_AGENT_PROPERTIES = "agentProperties";
    public static final String KEY_ROUTING_DECISION = "routingDecision";
    public static final String KEY_GENERATED_RESPONSE = "generatedResponse";
    public static final String KEY_VALIDATION_PASSED = "validationPassed";
    public static final String KEY_VALIDATION_FAILURE_REASON = "validationFailureReason";
    public static final String KEY_RETRIED = "retried";

    /**
     * Constructs an AgentState with the given initial data.
     *
     * @param initData the initial data for the agent state
     */
    public WorkflowState(Map<String, Object> initData) {
        super(initData);
    }

    public UUID runId() {
        return this.<UUID>value(KEY_RUN_ID)
                .orElseThrow(() -> new IllegalStateException("runID not set in workflow state"));
    }

    public UUID conversationId() {
        return this.<UUID>value(KEY_CONVERSATION_ID)
                .orElseThrow(() -> new IllegalStateException("conversationID not set in workflow state"));
    }

    public String userMessage() {
        return this.<String>value(KEY_USER_MESSAGE)
                .orElseThrow(() -> new IllegalStateException("userMessage not set in workflow state"));
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

    public AgentProperties agentProperties() {
        return this.<AgentProperties>value(KEY_AGENT_PROPERTIES)
                .orElseThrow(() -> new IllegalStateException("agentProperties not set in workflow state"));
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
}