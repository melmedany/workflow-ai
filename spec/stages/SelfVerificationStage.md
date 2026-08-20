# SelfVerificationStage

## Purpose

Gate `EXECUTE_WORKFLOW`'s draft response against the agent's response contract: pass it through if it's already valid,
otherwise attempt exactly one retry, then accept whatever the retry produced as final either way.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `agentProperties()`, `validationPassed()`, `retried()`,
  `generatedResponse()`, `validationFailureReason()`, `userMessage()`, `systemPrompt()`, `memoryContext()`.
- Expects to run immediately after `EXECUTE_WORKFLOW` in the graph (`EXECUTE_WORKFLOW -> SELF_VERIFICATION ->
  COMPACT_MEMORY`). There is no graph edge back into this stage, so it only ever executes once per turn.

## Outputs / constraints / postconditions / invariants

- If `state.validationPassed()` is already `true` (the draft passed validation in `EXECUTE_WORKFLOW`), the stage
  persists/emits that draft as final via `PersistResponseStage`, and returns `KEY_GENERATED_RESPONSE` = the draft and
  `KEY_VALIDATION_PASSED = true`, with no provider call.
- Otherwise, exactly one retry is attempted: a corrective prompt (`WorkflowPrompts.retryPrompt`) referencing the
  original message, the invalid draft, and the failure reason is sent to the agent's own provider (buffered
  `stream(...)`), and the result is re-validated.
- Regardless of whether the retry itself passes re-validation, the retry's response becomes the final response and
  `KEY_VALIDATION_PASSED` is unconditionally `true`, and `KEY_RETRIED` is `true`, this stage's retry budget is exactly
  one attempt. After that, the best available response is accepted rather than looping or failing the turn.
- The retry's response is always persisted and emitted via `PersistResponseStage.finalizeResponse`, whether the retry
  passed validation.
- `stageStarted(runId, SELF_VERIFICATION)` is emitted at the very start of every execution. Exactly one of
  `stageCompleted(runId, SELF_VERIFICATION)` (already-valid draft, or retry that passed re-validation) or
  `stageFailed(runId, SELF_VERIFICATION, reason)` (retry that still failed re-validation) is emitted afterward, the
  choice signals the outcome quality to SSE clients even though the turn proceeds either way.

## Interfaces

- `StageId stageId()` -> `StageId.SELF_VERIFICATION`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- Already-valid draft: no provider call, `stageCompleted` emitted, response unchanged.
- Invalid draft, retry passes validation: exactly one provider call, `stageCompleted` emitted, `KEY_RETRIED = true`,
  `KEY_VALIDATION_PASSED = true`, response = retry text.
- Invalid draft, retry still fails validation: exactly one provider call, `stageFailed` emitted (not
  `stageCompleted`) with a reason mentioning the validation failure, `KEY_RETRIED = true`, `KEY_VALIDATION_PASSED =
  true` (best-effort acceptance), response = retry text.
- The final response is persisted exactly once regardless of path.

## Failure modes

- A provider error during the retry call is not caught here and propagates to the caller.
- `stageFailed` (not an exception) is the mechanism used to signal "retries still invalid", the turn is not aborted.

## Edge Cases

- `state.retried()` already `true` when this stage starts (would mean a second entry into this stage for the same run),
  the code path for this exists (skip the retry, emit `stageFailed`, use the existing generated response as final) but
  is unreachable with the current graph wiring, since nothing routes back into `SELF_VERIFICATION`.

## Non-goals

- Does not attempt more than one retry.
- Does not fail the overall turn when the retry is still invalid, always produces a best-effort final response.
