package io.workflowai.application.pipeline;

import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.domain.model.RoutingDecision;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class WorkflowContext extends AgentState {

    public static final AgentStateFactory<WorkflowContext> SCHEMA = WorkflowContext::new;

    protected static final String KEY_RUN_ID = "runId";
    protected static final String KEY_CONVERSATION_ID = "conversationId";
    protected static final String KEY_USER_MESSAGE = "userMessage";
    protected static final String KEY_HISTORY = "history";
    protected static final String KEY_SYSTEM_PROMPT = "systemPrompt";
    protected static final String KEY_ROUTING_DECISION = "routingDecision";
    protected static final String KEY_GENERATED_RESPONSE = "generatedResponse";
    protected static final String KEY_VALIDATION_PASSED = "validationPassed";
    protected static final String KEY_RETRIED = "retried";

    public WorkflowContext(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<UUID> runId() {
        return value(KEY_RUN_ID);
    }

    public Optional<UUID> conversationId() {
        return value(KEY_CONVERSATION_ID);
    }

    public String userMessage() {
        return this.<String>value(KEY_USER_MESSAGE)
                .orElseThrow(() -> new IllegalStateException("userMessage not set in workflow context"));
    }

    public List<ConversationMessage> history() {
        return this.<List<ConversationMessage>>value(KEY_HISTORY).orElse(List.of());
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

    public boolean retried() {
        return this.<Boolean>value(KEY_RETRIED).orElse(false);
    }
}
