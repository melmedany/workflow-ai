# User input execution (ExecuteWorkflowStage)

## Purpose

Generate a draft response with the agent's configured model and assess it against the agent's response contract before
anything is persisted or shown to the user.

## Requirements

- Generation must use the agent's model, temperature, provider, system prompt, user message, and conversation memory.
- When the response contract requires JSON, the generation instructions must include that contract.
- The complete draft must be available before validation begins.
- The result must contain the draft, whether validation passed, and the validation reason. The reason is empty when
  validation succeeds.
- A draft must not be persisted or emitted to the client because self-verification may replace it.
- An execution-started event must precede generation. An execution-completed event must follow every successful draft or
  handled guardrail outcome.

## Failure behaviour

- If input guardrails block generation, the result must be the agent policy's `failedToProcessMessage` text and must be
  treated as already valid. Revalidation and retry are skipped because the same input would be blocked again.
- Other provider or generation failures terminate the stage and propagate to the workflow.
- A response-contract failure is not a stage failure. The invalid draft and reason continue to self-verification.

## Acceptance criteria

- A valid draft is returned unchanged with validation marked as passed and no failure reason.
- An invalid draft is returned unchanged with validation marked as failed and the validator's reason.
- A guardrail block returns the policy fallback as an accepted response without response-contract validation.
- Draft text is never persisted or emitted from this stage.
- Exactly one started and one completed event is emitted for valid, invalid, and guardrail-fallback results.
