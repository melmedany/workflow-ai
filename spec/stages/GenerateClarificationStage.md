# GenerateClarificationStage

## Purpose

Produce the single clarifying question shown to the user for a `CLARIFY` decision, preferring the question the
classifier already extracted and only falling back to a dedicated model call when it didn't provide one.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `routingDecision()`, `userMessage()`, `systemPrompt()`, `memoryContext()`, and
  `agentProperties().id()`.
- The stage's own model/provider/temperature (used only for the fallback path) comes from `StageSettings` for
  `StageId.GENERATE_CLARIFICATION`.

## Outputs / constraints / postconditions / invariants

- If `routingDecision()` is present and its `clarificationQuestion()` is non-null and non-blank, that question is used
  verbatim. No chat provider call is made.
- Otherwise (decision absent, or `clarificationQuestion()` is `null`/blank), a question is generated via a single,
  non-streamed call to the configured provider (`ChatProviderRegistry...call(...)`), using
  `WorkflowPrompts.clarificationPrompt(userMessage())` as the prompt.
- The resulting text (whichever source) is persisted and emitted via `PersistResponseStage.finalizeResponse`.
- Returns a map containing only `WorkflowState.KEY_GENERATED_RESPONSE`, this stage does not set
  `KEY_VALIDATION_PASSED`, because the `CLARIFY` path never routes through `SELF_VERIFICATION`
  (`GENERATE_CLARIFICATION -> COMPACT_MEMORY -> COMPLETE`).
- Emits `stageStarted(runId, GENERATE_CLARIFICATION)` before either path runs, and `stageCompleted(runId,
  GENERATE_CLARIFICATION)` once the question (classifier-provided or generated) is determined, before persisting it.

## Interfaces

- `StageId stageId()` -> `StageId.GENERATE_CLARIFICATION`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- A decision with a non-blank `clarificationQuestion` is used as-is, and the chat provider is never called.
- A decision with a blank/whitespace-only or absent `clarificationQuestion` triggers exactly one call to the configured
  provider, and that provider's return value becomes the persisted/returned response.
- No `routingDecision()` at all also triggers the provider-generated path (does not throw).
- The final text is persisted as an `AGENT` conversation message via `PersistResponseStage`.
- `stageStarted`/`stageCompleted` each fire exactly once per execution, for every registered `WorkflowEventStreamer`.

## Failure modes

- If the provider call throws (fallback path only), the exception propagates uncaught.

## Edge Cases

- `clarificationQuestion()` is `null`, handled the same as absent/blank (no NPE), falls back to generation.

## Non-goals

- Does not decide *whether* to clarify, that decision (`CLARIFY` mode) is made upstream by `CLASSIFICATION` (or, for
  scheduling, by `CREATE_TASK`).
- Does not validate the generated question against a response contract, this path bypasses `SELF_VERIFICATION`
  entirely.
