# GenerateGreetingStage

## Purpose

Produce a short, in-persona greeting stating what the agent can help with, for a `GREET` decision.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `routingDecision()`, `systemPrompt()`, `userMessage()`, and
  `agentProperties()` (`id()`, `workflowPolicy()`).
- If `routingDecision()` is absent, a default `RoutingDecision.greet("Greeting", userMessage())` is used instead. This
  stage never fails purely for lack of a decision.

## Outputs / constraints / postconditions / invariants

- Delegates text generation to `DecisionResponseGenerator.generate(...)` with a prompt built by
  `WorkflowPrompts.greetingPrompt(systemPrompt, workflowPolicy, decision)`.
- The resulting text (generated or the generator's fallback on failure) is persisted and emitted via
  `PersistResponseStage.finalizeResponse`.
- Returns a map containing only `WorkflowState.KEY_GENERATED_RESPONSE`, no `KEY_VALIDATION_PASSED`, since this path
  bypasses `SELF_VERIFICATION` (`GENERATE_GREETING -> COMPACT_MEMORY -> COMPLETE`).
- Emits `stageStarted(runId, GENERATE_GREETING)` before generation, and `stageCompleted(runId, GENERATE_GREETING)`
  once generation completes (successfully or via fallback), before persisting.

## Interfaces

- `StageId stageId()` → `StageId.GENERATE_GREETING`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- With a routing decision present, its `reason`/`extractedIntent` feed the prompt.
- With no routing decision, a default GREET decision is synthesized instead of throwing.
- The final text is persisted as an `AGENT` conversation message.
- `stageStarted`/`stageCompleted` each fire exactly once per execution, on every registered `WorkflowEventStreamer`.
- If the underlying provider call fails, the policy's `failedToProcessMessage()` is used and returned as
  `KEY_GENERATED_RESPONSE` (via `DecisionResponseGenerator`'s fallback) rather than propagating an exception.

## Failure modes

- Model/provider failures never propagate, always absorbed by `DecisionResponseGenerator` into the fallback message.

## Edge Cases

- `routingDecision()` present but with a decision mode other than `GREET` (e.g. stale state), still used as-is. This
  stage does not re-validate the decision mode.

## Non-goals

- Does not decide *whether* to greet, that's `CLASSIFICATION`'s responsibility.
- Does not validate the generated greeting against a response contract.
