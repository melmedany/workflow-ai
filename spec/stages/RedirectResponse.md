# Redirect response (GenerateRedirectStage)

## Purpose

Guide a mixed-scope request toward the part the agent can handle.

## Requirements

- A present routing decision must inform the redirect. If it is absent, the request must be treated as a `REDIRECT`
  decision using the original user message.
- Generation must use the agent's system context, policy, memory, and routing context.
- The response must focus on the in-scope portion identified by classification.
- A generation failure must use the agent policy's `failedToProcessMessage` text rather than fail the workflow.
- A redirect-started event must precede generation, and a redirect-completed event must be emitted once the response
  text is available.
- The completed stage event must precede response persistence and emission.
- The final text must be persisted as an `AGENT` message before its ordered tokens and response-completed event are
  emitted.
- Redirect responses bypass response-contract validation and self-verification.

## Acceptance criteria

- Routing reason and extracted intent contribute to the redirect when available.
- Missing routing state still produces a redirect rather than failing.
- Provider failure produces and persists the configured fallback.
- One started and one completed stage event precede the persisted and emitted final response.
