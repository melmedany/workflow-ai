package io.workflowai.application.execution.workflow;

import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.domain.workflow.response.ResponseContract;
import io.workflowai.domain.workflow.response.ResponseFormat;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.util.StringUtils.hasText;

public final class WorkflowPrompts {

    public static final String CLASSIFICATION_SYSTEM_PROMPT = """
            You are a workflow classification assistant. Your task is to evaluate user requests and produce a routing decision.
            You MUST respond with a single valid JSON object matching this exact schema, with no additional text,
            NO MARKDOWN CODE fence (no ``` characters), and NO EXPLANATION before or after the JSON.
            Evaluate whether the User input qualifies with the provided "Supported capabilities". If it requests an action not
            explicitly allowed, set "decisionMode" to "REFUSE" and explain why in "reason".

            The caller provides a "scheduleMode" variable:
            - "OFF": classify the request using the normal routing rules. Scheduling fields MUST all be null.
            - "ON": apply both the normal routing rules and the scheduling rules below. Populate scheduling fields when
              the request contains a valid scheduling instruction; otherwise keep them null.

            Unified schema (include ALL of these fields in every response):
            {
              "decisionMode": "<one of: EXECUTE, CLARIFY, REDIRECT, REFUSE, GREET>",
              "detectedTopics": ["topic1", "topic2"],
              "extractedIntent": "brief description of what the user wants",
              "clarificationQuestion": "question to ask the user, only set when decisionMode is CLARIFY, otherwise null",
              "reason": "brief reason for this decision",
              "scheduleType": "<one of: ONCE, RECURRING, null when scheduleMode is OFF or the request is not schedulable>",
              "startDateTime": "An ISO-8601 24h time string. Examples: '2026-08-10T09:00:00Z', '2026-08-15T14:45:00Z', '2026-08-12T00:00:00Z' (Midnight), null when scheduleMode is OFF or no relative schedule start time applies",
              "duration": "An ISO-8601 duration string. Examples: 'PT45M' (45 mins), 'PT1H30M' (1.5 hours), 'P1D' (1 day), null when scheduleMode is OFF or no relative schedule duration applies",
              "scheduleInstruction": "plain-text instruction to run on each occurrence, with no timing/frequency wording, null when scheduleMode is OFF or the request is not schedulable"
            }

            decisionMode rules:
            - EXECUTE: request is clearly within scope and actionable.
            - CLARIFY: request is in scope but missing required information. In scheduleMode ON, use this when the core action
              or target execution time is completely ambiguous.
            - REDIRECT: request contains mixed content (some in scope, some not).
            - REFUSE: request is completely out of scope or unsafe. In scheduleMode ON, also refuse a scheduling instruction that
              attempts to create or modify another recurring task, uses an invalid frequency (for example, sub-minute intervals),
              or falls outside the agent's supported capabilities. These checks apply every time because scheduled instructions
              will run automatically without further review.
            - GREET: request is a greeting or small talk with no task content.

            Scheduling rules (apply ONLY when scheduleMode is ON):
            1. Classify "scheduleType" as "ONCE" for a single future event or "RECURRING" for a repeating pattern.
               If a scheduling request is present but the recurrence pattern is unclear, default to "ONCE".
            2. Extract a relative time period into "duration" using strict ISO-8601 format.
                - Minutes/Hours MUST use 'T' (e.g., "PT2M", "PT1H").
                - Days MUST NOT use 'T' (e.g., 1 day is "P1D", 2 days is "P2D").
                - CRITICAL: "PT1D" is mathematically invalid syntax. You are strictly forbidden from outputting "PT1D".
            3. CRITICAL: Clean "scheduleInstruction" by removing raw timing, frequency, and scheduling command-prefix wording.
               (for example, "every 5 mins run backup" becomes "Run backup").
            4. "scheduleInstruction" must contain only the action to execute, not when, how often, or scheduling metadata.
            5. For a valid scheduling request, apply the same capability check as a normal request before returning EXECUTE.
            6. When scheduleMode is OFF, do not infer or populate any scheduling fields even if the user mentions a schedule.
            """;

    public static final String GUARDRAIL_FALLBACK_MESSAGE =
            "I'm not able to share that response. Let me know if I can help with something else.";

    private WorkflowPrompts() {
    }

    public static String classificationPrompt(UUID agentId, WorkflowPolicy policy, String userMessage,
                                               boolean schedulingRequested) {
        String capabilitiesList = String.join(", ", policy.supportedCapabilities());
        String scheduleMode = schedulingRequested ? "ON" : "OFF";
        return """
                Agent: %s
                Supported capabilities: %s
                scheduleMode: %s
                User input: %s
                Current date and time: %s
                """.formatted(agentId, capabilitiesList, scheduleMode, userMessage, Instant.now());
    }

    public static String clarificationPrompt(String userMessage) {
        return "The user's request needs clarification. Ask one targeted question to understand what they need: "
                + userMessage;
    }

    public static String greetingPrompt(String systemPrompt, WorkflowPolicy policy, RoutingDecision decision) {
        return decisionResponsePrompt(systemPrompt, policy, decision, "Greet the user briefly and state what this agent can help with.");
    }

    public static String refusalPrompt(String systemPrompt, WorkflowPolicy policy, RoutingDecision decision) {
        return decisionResponsePrompt(systemPrompt, policy, decision, "Politely decline the out-of-scope or unsafe request without providing the disallowed help, justified by the provided reason.");
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
                """.formatted(hasText(previousMemory) ? previousMemory : "", userMessage, response);
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

                %s
                
                Produce a short, on-persona answer.
                """.formatted(
                systemPrompt,
                String.join(", ", policy.supportedCapabilities()),
                hasText(decision.reason()) ? decision.reason() : "",
                hasText(decision.extractedIntent()) ? decision.extractedIntent() : "",
                instruction);
    }
}