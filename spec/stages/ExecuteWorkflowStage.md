# ExecuteWorkflowStage

## Purpose

Run the agent's own configured model against the request to produce a draft response and validate that draft against the
agent's response contract, so `SELF_VERIFICATION` can decide whether to accept or retry it.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `systemPrompt()`, `userMessage()`, `memoryContext()`, and
  `agentProperties()` (`model()`, `temperature()`, `chatProviderId()`, `workflowPolicy().responseContract()`,
  `workflowPolicy().failedToProcessMessage()`).
- Assumes the configured chat provider is registered in `ChatProviderRegistry`.

## Outputs / constraints / postconditions / invariants

- Builds a `ChatCompletionRequest` whose system prompt is `state.systemPrompt()` augmented with response-contract
  instructions (`withResponseContractInstructions`) when the contract format is JSON.
- Calls the provider's buffered `stream(...)` with a no-op token consumer, this is a *draft* generation only. Per the
  implementation comment, the result must not be persisted or streamed to the client here because
  `SELF_VERIFICATION` may still trigger a retry.
- On success, returns `KEY_GENERATED_RESPONSE` (the raw draft text), `KEY_VALIDATION_PASSED` (from
  `ResponseValidator`), and `KEY_VALIDATION_FAILURE_REASON` (the validator's reason, or `""` when valid).
- When the provider call fails with `GuardrailBlockedException` (input blocked at the provider boundary), the stage
  short-circuits: it returns `KEY_GENERATED_RESPONSE` = `workflowPolicy().failedToProcessMessage()` and
  `KEY_VALIDATION_PASSED` = `true`, without calling `ResponseValidator`, a retry through `SELF_VERIFICATION` would hit
  the same guardrail block again, so this response is treated as already final and safe.
- Emits `stageStarted(runId, EXECUTE_WORKFLOW)` at the start of every execution, and `stageCompleted(runId,
  EXECUTE_WORKFLOW)` exactly once per execution, including the guardrail-blocked short-circuit path, since that path
  still represents the stage having finished producing its (fallback) output.

## Interfaces

- `StageId stageId()` -> `StageId.EXECUTE_WORKFLOW`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- A successful provider call with a response that passes validation returns the response with
  `KEY_VALIDATION_PASSED = true` and an empty failure reason.
- A successful provider call with a response that fails validation returns the response as-is with
  `KEY_VALIDATION_PASSED = false` and a non-empty failure reason from `ResponseValidator`, this stage does not retry or
  reject the response itself.
- A `GuardrailBlockedException` from the provider call is caught, and the stage returns the policy's
  `failedToProcessMessage()` as the generated response with `KEY_VALIDATION_PASSED = true`, without invoking
  `ResponseValidator`.
- `stageStarted` and `stageCompleted` are each emitted exactly once per execution, on every registered
  `WorkflowEventStreamer`, on every path (success, validation failure, and guardrail-blocked fallback).

## Failure modes

- `GuardrailBlockedException` -> caught, converted into a safe fallback response (see above). Not propagated.
- Any other exception from the provider call (timeout, unknown provider, etc.) is not caught here and propagates to the
  caller.

## Edge Cases

- Response contract is the default `ResponseContract.text()` (free text, no min length, no required fields), validation
  always succeeds for any non-blank response.
- Validator reports an invalid response, the stage still returns normally (no exception). It is up to
  `SELF_VERIFICATION` to act on `KEY_VALIDATION_PASSED = false`.

## Non-goals

- Does not persist the draft response or stream tokens to the client, that happens only once the response is confirmed
  final (via `PERSIST_RESPONSE`).
- Does not retry generation itself on validation failure, retry orchestration belongs to `SELF_VERIFICATION`.
- Does not apply guardrails directly, guard-railing happens inside the chat provider call.
