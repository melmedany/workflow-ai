# Clarification response (GenerateClarificationStage)

## Purpose

Produce the single question shown when the workflow needs more information from the user.

## Requirements

- A non-blank clarification question already present in the routing decision must be used verbatim without another model
  request.
- If the decision or its clarification question is absent or blank, one question must be generated from the original
  user message and the conversation context.
- A clarification-started event must precede selection or generation of the question.
- Once the question is available, a clarification-completed event must be emitted before response persistence or token
  emission.
- The final question must be persisted as an `AGENT` conversation message before any token is emitted.
- The persisted text must then be emitted in token order, followed by a response-completed event.
- Clarification responses bypass response-contract validation and self-verification.

## Failure behaviour

- A failure while generating a missing clarification question terminates the stage and propagates to the workflow.
- A persistence failure must occur before token emission, so an unpersisted question is never streamed as final.

## Acceptance criteria

- A supplied non-blank question is persisted and emitted unchanged without model generation.
- A missing or whitespace-only question causes exactly one generated question to be persisted and emitted.
- An absent routing decision uses the generated-question path.
- One started and one completed stage event precede the final response emission.
