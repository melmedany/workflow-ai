package io.workflowai.domain.model;

import io.workflowai.domain.workflow.DecisionMode;

import java.io.Serializable;
import java.util.List;

public record RoutingDecision(
        DecisionMode decisionMode,
        List<String> detectedTopics,
        String extractedIntent,
        String clarificationQuestion,
        String reason) implements Serializable {

    public static RoutingDecision greet() {
        return new RoutingDecision(DecisionMode.GREET, List.of(), null, null, null);
    }

    public static RoutingDecision refuse(String reason, String extractedIntent) {
        return new RoutingDecision(DecisionMode.REFUSE, List.of(), extractedIntent, null, reason);
    }

    public static RoutingDecision execute(String intent, List<String> topics) {
        return new RoutingDecision(DecisionMode.EXECUTE, topics, intent, null, "Request is valid and actionable");
    }
}
