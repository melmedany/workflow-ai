# Greeting response (GenerateGreetingStage)

## Purpose

Produce a short greeting in the agent's persona that states what the agent can help with.

## Requirements

- A present routing decision must inform the greeting. If it is absent, the request must be treated as a `GREET`
  decision using the original user message.
- Generation must use the agent's system context, policy, memory, and routing context.
- A generation failure must use the agent policy's `failedToProcessMessage` text rather than fail the workflow.
- A greeting-started event must precede generation, and a greeting-completed event must be emitted once response text is
  available.
- The completed stage event must precede response persistence and emission.
- The final text must be persisted as an `AGENT` message before its ordered tokens and response-completed event are
  emitted.
- Greeting responses bypass response-contract validation and self-verification.

## Acceptance criteria

- Routing reason and extracted intent contribute to the generated greeting when available.
- Missing routing state still produces a greeting rather than failing.
- Provider failure produces and persists the configured fallback.
- One started and one completed stage event precede the persisted and emitted final response.
