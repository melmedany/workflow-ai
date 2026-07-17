package io.workflowai.domain.workflow;

import java.util.UUID;

public final class WorkflowPrompts {

    public static final String CLASSIFICATION_SYSTEM_PROMPT = """
            You are a workflow classification assistant. Your task is to evaluate user requests and produce a routing decision.
            You MUST respond with a single valid JSON object matching this exact schema, with no additional text:
            {
              "decisionMode": "EXECUTE|CLARIFY|REDIRECT|REFUSE",
              "detectedTopics": ["topic1", "topic2"],
              "extractedIntent": "brief description of what the user wants",
              "clarificationQuestion": "question to ask the user (non-null only when CLARIFY)",
              "reason": "brief reason for this decision"
            }
            Decision rules:
            - EXECUTE: request is clearly within scope and actionable
            - CLARIFY: request is in scope but missing required information
            - REDIRECT: request contains mixed content (some in scope, some not)
            - REFUSE: request is completely out of scope or unsafe
            """;

    private WorkflowPrompts() {
    }

    public static String classificationPrompt(UUID agentId, WorkflowPolicy policy, String userMessage) {
        String capabilitiesList = String.join(", ", policy.supportedCapabilities());
        return """
                Agent: %s
                Supported capabilities: %s
                User request: %s
                """.formatted(agentId, capabilitiesList, userMessage);
    }

    public static String clarificationPrompt(String userMessage) {
        return "The user's request needs clarification. Ask one targeted question to understand what they need: "
                + userMessage;
    }

    public static String retryPrompt(String userMessage, String previousResponse) {
        return "Your previous response had quality issues. Please improve it. Original request: "
                + userMessage
                + "\n\nPrevious response:\n" + previousResponse
                + "\n\nProvide a corrected, complete response.";
    }
}