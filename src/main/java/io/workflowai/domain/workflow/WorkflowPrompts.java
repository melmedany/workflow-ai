package io.workflowai.domain.workflow;

import io.workflowai.domain.model.RoutingDecision;
import io.workflowai.domain.workflow.response.ResponseContract;
import io.workflowai.domain.workflow.response.ResponseFormat;

import java.util.UUID;

public final class WorkflowPrompts {

    public static final String CLASSIFICATION_SYSTEM_PROMPT = """
            You are a workflow classification assistant. Your task is to evaluate user requests and produce a routing decision.
            You MUST respond with a single valid JSON object matching this exact schema, with no additional text:
            {
              "decisionMode": "EXECUTE|CLARIFY|REDIRECT|REFUSE|GREET",
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
            - GREET: request is a greeting or small talk with no task content
            """;

    public static final String GUARDRAIL_FALLBACK_MESSAGE =
            "I'm not able to share that response. Let me know if I can help with something else.";

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

    public static String greetingPrompt(String systemPrompt, WorkflowPolicy policy, RoutingDecision decision) {
        return decisionResponsePrompt(systemPrompt, policy, decision, "Greet the user briefly and state what this agent can help with.");
    }

    public static String refusalPrompt(String systemPrompt, WorkflowPolicy policy, RoutingDecision decision) {
        return decisionResponsePrompt(systemPrompt, policy, decision, "Politely decline the out-of-scope or unsafe request without providing the disallowed help.");
    }

    public static String redirectPrompt(String systemPrompt, WorkflowPolicy policy, RoutingDecision decision) {
        return decisionResponsePrompt(systemPrompt, policy, decision, "Briefly redirect the user toward the in-scope part of the request and explain what can be handled.");
    }

    public static String memoryCompactionPrompt(String previousMemory, String userMessage, String response) {
        return """
                Update the compact conversation memory for future turns. Keep durable preferences, facts, goals, constraints, and decisions.
                Remove transient wording and do not invent facts. Return only the compact memory text.

                Previous memory:
                %s

                Latest user message:
                %s

                Latest agent response:
                %s
                """.formatted(blankIfNull(previousMemory), userMessage, response);
    }

    public static String retryPrompt(String userMessage, String previousResponse, String failureReason) {
        return "Your previous response failed validation and must be corrected. Original request: "
                + userMessage
                + "\n\nPrevious response:\n" + previousResponse
                + "\n\nValidation problem: " + failureReason
                + "\n\nProvide a corrected, complete response that fixes this specific problem.";
    }

    public static String withResponseContractInstructions(String systemPrompt, ResponseContract contract) {
        if (contract.format() != ResponseFormat.JSON) {
            return systemPrompt;
        }

        String instruction = contract.requiredFields().isEmpty()
                ? "Respond with a single valid JSON object and no additional text before or after it. "
                        + "Do not wrap it in a markdown code fence (no ``` characters)."
                : "Respond with a single valid JSON object and no additional text before or after it. "
                        + "Do not wrap it in a markdown code fence (no ``` characters). "
                        + "The JSON object must contain at least these top-level fields: "
                        + String.join(", ", contract.requiredFields()) + ".";
        return systemPrompt + "\n\n" + instruction;
    }

    private static String decisionResponsePrompt(String systemPrompt, WorkflowPolicy policy, RoutingDecision decision, String instruction) {
        return """
                Agent persona/system prompt:
                %s

                Supported capabilities:
                %s

                Classification reason:
                %s

                Extracted intent:
                %s

                %s Produce a short, on-persona answer.
                """.formatted(
                systemPrompt,
                String.join(", ", policy.supportedCapabilities()),
                blankIfNull(decision.reason()),
                blankIfNull(decision.extractedIntent()),
                instruction);
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}