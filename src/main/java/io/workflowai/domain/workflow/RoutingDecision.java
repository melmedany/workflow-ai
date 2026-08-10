package io.workflowai.domain.workflow;

import java.io.Serializable;
import java.util.List;

public record RoutingDecision(
        DecisionMode decisionMode,
        List<String> detectedTopics,
        String extractedIntent,
        String clarificationQuestion,
        String reason,
        String cronExpression,
        String runOnceAt,
        String scheduleInstruction) implements Serializable {

    public RoutingDecision(DecisionMode decisionMode, List<String> detectedTopics, String extractedIntent,
                           String clarificationQuestion, String reason) {
        this(decisionMode, detectedTopics, extractedIntent, clarificationQuestion, reason, null, null, null);
    }

    public static RoutingDecision greet(String reason, String extractedIntent) {
        return new RoutingDecision(DecisionMode.GREET, List.of(), extractedIntent, null, reason);
    }

    public static RoutingDecision redirect(String reason, String extractedIntent) {
        return new RoutingDecision(DecisionMode.REDIRECT, List.of(), extractedIntent, null, reason);
    }

    public static RoutingDecision refuse(String reason, String extractedIntent) {
        return new RoutingDecision(DecisionMode.REFUSE, List.of(), extractedIntent, null, reason);
    }

    public static RoutingDecision clarify(String reason, String extractedIntent, String clarificationQuestion) {
        return new RoutingDecision(DecisionMode.CLARIFY, List.of(), extractedIntent, clarificationQuestion, reason);
    }
}
