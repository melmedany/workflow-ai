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

    public static RoutingDecision greet(String reason, String extractedIntent) {
        return new RoutingDecision(DecisionMode.GREET, List.of(), extractedIntent, null, reason);
    }

    public static RoutingDecision redirect(String reason, String extractedIntent) {
        return new RoutingDecision(DecisionMode.REDIRECT, List.of(), extractedIntent, null, reason);
    }

    public static RoutingDecision refuse(String reason, String extractedIntent) {
        return new RoutingDecision(DecisionMode.REFUSE, List.of(), extractedIntent, null, reason);
    }
}
