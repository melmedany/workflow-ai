# DecisionResponseGenerator

## Purpose

Shared helper that turns a pre-built prompt into a short-generated response for the three decision-response stages
(`GENERATE_GREETING`, `GENERATE_REDIRECT`, `GENERATE_REFUSAL`), and absorbs any model/provider failure into a safe
fallback message instead of letting it propagate.

## Inputs / constraints / preconditions

- `generate(WorkflowState state, StageId stageId, String prompt)` reads `state.systemPrompt()`,
  `state.memoryContext()`, and `state.agentProperties()` (`id()`, `workflowPolicy().failedToProcessMessage()`).
- `stageId` must have a configured entry in `StageSettings` (used to resolve model/provider/temperature).

## Outputs / constraints / postconditions / invariants

- On a successful provider call, returns the provider's (already output-guardrailed) response text unmodified.
- On ANY exception during request building or the provider call (unknown provider, unsupported model, timeout,
  guardrail block, etc.), the exception is caught and `state.agentProperties().workflowPolicy()
  .failedToProcessMessage()` is returned instead, this method never throws.
- Uses the provider's buffered `stream(...)` call with a no-op token consumer (consistent with `EXECUTE_WORKFLOW`'s
  draft-generation pattern), not `call(...)`.

## Interfaces

- `String generate(WorkflowState state, StageId stageId, String prompt)` (package-private used only by
  `application.execution.stage` classes in the same package)

## Acceptance criteria

- A successful call returns the provider's text as-is.
- Any exception thrown while resolving the provider, building the request, or streaming results in the configured
  `failedToProcessMessage()` being returned, and no exception escapes `generate`.

## Failure modes

- Every failure category (bad config, network, guardrail) is treated identically: swallowed and replaced with the
  policy's fallback message. Per the class Javadoc, this is intentional. The output guardrail already ran inside
  the provider call, so a failure here is always an infra/model problem, not a content-safety one.

## Edge Cases

- `workflowPolicy().failedToProcessMessage()` itself being blank/null is not handled specially. Whatever is
  configured is returned verbatim.

## Non-goals

- Does not persist or stream the result, that's the caller's (`PersistResponseStage`) job.
- Does not retry the provider call on failure.
