# GenerateRefusalStage

## Purpose

Politely decline an out-of-scope or unsafe request, for a `REFUSE` decision, without providing the disallowed help.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `routingDecision()`, `systemPrompt()`, `userMessage()`, and
  `agentProperties()` (`id()`, `workflowPolicy()`).
- If `routingDecision()` is absent, a default `RoutingDecision.refuse("Refusing request", userMessage())` is used
  instead.

## Outputs / constraints / postconditions / invariants

- Delegates text generation to `DecisionResponseGenerator.generate(...)` with a prompt built by
  `WorkflowPrompts.refusalPrompt(systemPrompt, workflowPolicy, decision)`.
- The resulting text is persisted and emitted via `PersistResponseStage.finalizeResponse`.
- Returns a map containing only `WorkflowState.KEY_GENERATED_RESPONSE`, no `KEY_VALIDATION_PASSED`, since this path
  bypasses `SELF_VERIFICATION` (`GENERATE_REFUSAL -> COMPACT_MEMORY -> COMPLETE`).
- Emits `stageStarted(runId, GENERATE_REFUSAL)` before generation, and `stageCompleted(runId, GENERATE_REFUSAL)`
  once generation completes, before persisting.

## Interfaces

- `StageId stageId()` → `StageId.GENERATE_REFUSAL`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- With a routing decision present, its `reason`/`extractedIntent` feed the prompt, justifying the refusal.
- With no routing decision, a default REFUSE decision is synthesized instead of throwing.
- The final text is persisted as an `AGENT` conversation message.
- `stageStarted`/`stageCompleted` each fire exactly once per execution, on every registered `WorkflowEventStreamer`.
- If the underlying provider call fails, the policy's `failedToProcessMessage()` is used instead of propagating an
  exception.

## Failure modes

- Model/provider failures never propagate, always absorbed by `DecisionResponseGenerator`.

## Non-goals

- Does not decide *whether* to refuse, that's `CLASSIFICATION`'s (or `CREATE_TASK`'s, for scheduling)
  responsibility.
- Does not validate the generated refusal against a response contract.
- Does not attempt to still provide the disallowed help in a softened form, the prompt explicitly instructs against
  that.
