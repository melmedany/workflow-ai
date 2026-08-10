package io.workflowai.application.execution.workflow;

import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.domain.workflow.response.ResponseContract;
import io.workflowai.domain.workflow.response.ResponseFormat;

import java.time.Instant;
import java.util.UUID;

public final class WorkflowPrompts {

    public static final String CLASSIFICATION_SYSTEM_PROMPT = """
            You are a workflow classification assistant. Your task is to evaluate user requests and produce a routing decision.
            You MUST respond with a single valid JSON object matching this exact schema, with no additional text,
            no markdown code fences, and no explanation before or after the JSON.
            {
              "decisionMode": "<one of: EXECUTE, CLARIFY, REDIRECT, REFUSE, GREET>",
              "detectedTopics": ["topic1", "topic2"],
              "extractedIntent": "brief description of what the user wants",
              "clarificationQuestion": "question to ask the user, only set when decisionMode is CLARIFY, otherwise null",
              "reason": "brief reason for this decision"
            }
            decisionMode must be exactly one of these five literal strings: EXECUTE, CLARIFY, REDIRECT, REFUSE, GREET.
            Decision rules:
            - EXECUTE: request is clearly within scope and actionable
            - CLARIFY: request is in scope but missing required information
            - REDIRECT: request contains mixed content (some in scope, some not)
            - REFUSE: request is completely out of scope or unsafe
            - GREET: request is a greeting or small talk with no task content

            Example output:
            {"decisionMode":"EXECUTE","detectedTopics":["weather"],"extractedIntent":"get tomorrow's forecast","clarificationQuestion":null,"reason":"clear, in-scope request"}
            """;

    public static final String SCHEDULING_CLASSIFICATION = """
            You are extracting the details of a recurring or one-time scheduled task from a user's message.
            The request has already been classified as EXECUTE or CLARIFY and confirmed to be a /schedule request.
            You MUST respond with a single valid JSON object matching this exact schema, with no additional text,
            no markdown code fences, and no explanation before or after the JSON.
            {
              "decisionMode": "<one of: EXECUTE, CLARIFY, REFUSE>",
              "clarificationQuestion": "question to ask the user, only set when decisionMode is CLARIFY, otherwise null",
              "reason": "brief reason for this decision",
              "cronExpression": "5-field UNIX cron expression, only set for recurring schedules, otherwise null",
              "runOnceAt": "ISO 8601 date-time (YYYY-MM-DDTHH:mm:ss.sssZ), only set for one-time schedules, otherwise null",
              "scheduleInstruction": "plain-text instruction to run on each occurrence, with no timing/frequency wording, otherwise null"
            }

            Current date and time: %s
            
            Rules:
            1. Exactly one of cronExpression or runOnceAt must be set — never both, never neither, unless decisionMode is CLARIFY or REFUSE (then both null).
            2. cronExpression must have EXACTLY 5 space-separated fields (minute hour day-of-month month day-of-week). Never 6 or 7 fields. Never use "?" — use "*" for unrestricted fields.
            3. Ignore end-time/duration phrases ("for the next hour", "until tomorrow") when building cronExpression — extract only the recurring pattern.
            4. scheduleInstruction must never contain timing or frequency wording.
            5. decisionMode CLARIFY (with a natural clarificationQuestion) when frequency or instruction is missing or ambiguous.
            6. decisionMode REFUSE when:
               - the instruction is itself a request to create/modify another recurring/scheduled task, or
               - the requested frequency is invalid (e.g. sub-minute intervals), or
               - the instruction falls outside the agent's supported capabilities listed below — this check applies
                 every time, since this instruction will be re-run automatically without further review.
    
            Examples:
            Input: "every 2 minutes say hello to the user"
            Output: {"decisionMode":"EXECUTE","clarificationQuestion":null,"reason":"clear recurring schedule, within capabilities","cronExpression":"*/2 * * * *","runOnceAt":null,"scheduleInstruction":"Say hello to the user"}
    
            Input (agent capabilities: "weather lookups only"): "every day at 8am check my email for invoices"
            Output: {"decisionMode":"REFUSE","clarificationQuestion":null,"reason":"email access is outside this agent's supported capabilities","cronExpression":null,"runOnceAt":null,"scheduleInstruction":null}
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
                User input: %s
                """.formatted(agentId, capabilitiesList, userMessage);
    }

    public static String classificationPrompt(UUID agentId, WorkflowPolicy policy, String userMessage,
                                               boolean schedulingRequested) {
        String base = classificationPrompt(agentId, policy, userMessage);
        if (!schedulingRequested) {
            return base;
        }
        return base + "\n" + SCHEDULING_CLASSIFICATION.formatted(Instant.now());
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