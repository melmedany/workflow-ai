# Refusal response (GenerateRefusalStage)

## Purpose

Politely decline an unsafe or out-of-scope request without providing the disallowed help.

## Requirements

- A present routing decision must inform the refusal. If it is absent, the request must be treated as a `REFUSE`
  decision using the original user message.
- Generation must use the agent's system context, policy, memory, and routing context.
- The response must not provide the help that the decision refused.
- A generation failure must use the agent policy's `failedToProcessMessage` text rather than fail the workflow.
- A refusal-started event must precede generation, and a refusal-completed event must be emitted once the response text
  is available.
- The completed stage event must precede response persistence and emission.
- The final text must be persisted as an `AGENT` message before its ordered tokens and response-completed event are
  emitted.
- Refusal responses bypass response-contract validation and self-verification.

## Acceptance criteria

- Routing reason and extracted intent contribute to the refusal when available.
- Missing routing state still produces a refusal rather than failing.
- Provider failure produces and persists the configured fallback.
- One started and one completed stage event precede the persisted and emitted final response.
