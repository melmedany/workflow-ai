# GenerateRedirectStage

## Purpose

Point a mixed-scope request at its in-scope part, for a `REDIRECT` decision.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `routingDecision()`, `systemPrompt()`, `userMessage()`, and
  `agentProperties()` (`id()`, `workflowPolicy()`).
- If `routingDecision()` is absent, a default `RoutingDecision.redirect("Redirecting mixed-scope request",
  userMessage())` is used instead.

## Outputs / constraints / postconditions / invariants

- Delegates text generation to `DecisionResponseGenerator.generate(...)` with a prompt built by
  `WorkflowPrompts.redirectPrompt(systemPrompt, workflowPolicy, decision)`.
- The resulting text is persisted and emitted via `PersistResponseStage.finalizeResponse`.
- Returns a map containing only `WorkflowState.KEY_GENERATED_RESPONSE`, no `KEY_VALIDATION_PASSED`, since this path
  bypasses `SELF_VERIFICATION` (`GENERATE_REDIRECT -> COMPACT_MEMORY -> COMPLETE`).
- Emits `stageStarted(runId, GENERATE_REDIRECT)` before generation, and `stageCompleted(runId, GENERATE_REDIRECT)`
  once generation completes, before persisting.

## Interfaces

- `StageId stageId()` → `StageId.GENERATE_REDIRECT`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- With a routing decision present, its `reason`/`extractedIntent` feed the prompt.
- With no routing decision, a default REDIRECT decision is synthesized instead of throwing.
- The final text is persisted as an `AGENT` conversation message.
- `stageStarted`/`stageCompleted` each fire exactly once per execution, on every registered `WorkflowEventStreamer`.
- If the underlying provider call fails, the policy's `failedToProcessMessage()` is used instead of propagating an
  exception.

## Failure modes

- Model/provider failures never propagate, always absorbed by `DecisionResponseGenerator`.

## Non-goals

- Does not decide *what part* of the request is in-scope, that's `CLASSIFICATION`'s responsibility, expressed via the
  decision's `reason`/`extractedIntent`.
- Does not validate the generated redirect against a response contract.
