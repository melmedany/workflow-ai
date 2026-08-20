# ClassificationStage

## Purpose

Produce the `RoutingDecision` that drives the rest of the workflow graph, and when the request is a scheduling request,
extract the schedule details (type, duration, instruction) as part of that same classification call.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `userMessage()`, `memoryContext()`, `agentProperties()` (for `id()` and
  `workflowPolicy()`), and `schedulingRequested()`.
- The stage's own model/provider/temperature comes from `StageSettings` for `StageId.CLASSIFICATION`. A missing
  configuration entry is a startup/config error, not something this stage recovers from.
- Assumes the configured chat provider is registered in `ChatProviderRegistry`.

## Outputs / constraints / postconditions / invariants

- Always returns a `Map` containing `WorkflowState.KEY_ROUTING_DECISION` bound to a non-null `RoutingDecision`.
- `decisionMode` in the returned decision is always one of `GREET`, `EXECUTE`, `CLARIFY`, `REDIRECT`, `REFUSE`. This
  stage never produces `EXECUTE_SCHEDULE` itself. That value is derived later in the workflow graph
  (`WorkflowExecutorFactory`) from an `EXECUTE` decision plus `schedulingRequested()`.
- When `schedulingRequested()` is `false`, the request is sent with `scheduleMode: OFF` and the resulting decision's
  scheduling fields (`scheduleType`, `startDateTime`, `duration`, `scheduleInstruction`) are expected to be `null`.
- When `schedulingRequested()` is `true`, `scheduleMode: ON` is sent and, for an `EXECUTE`/schedulable decision, the
  scheduling fields are expected to be populated by the model.
- Emits, in order, on every registered `WorkflowEventStreamer`: `stageStarted(runId, CLASSIFICATION)`, then either
  `stageCompleted(runId, CLASSIFICATION)` + `decisionMade(runId, decision)` on a normal (including fallback) result.

## Interfaces

- `StageId stageId()` -> `StageId.CLASSIFICATION`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- A well-formed classifier JSON response is parsed into the matching `RoutingDecision` and returned under
  `KEY_ROUTING_DECISION`.
- `stageStarted`/`stageCompleted`/`decisionMade` are each called exactly once per execution, for every configured
  streamer, with the state's `runId()`.
- Any error while calling the chat provider (network error, unsupported model, provider exception, etc.) or not
  valid/parseable JSON (or does not match the `RoutingDecision` shape) results in a fallback
  `RoutingDecision.refuse(...)` whose `reason` starts with `"Classification unavailable: "` and includes the original
  error message, and whose `extractedIntent` is the original `userMessage()` the stage does not throw for this class of
  failure.

## Failure modes

- Provider/network failure (timeout, unknown provider, unsupported model, guardrail block, etc.) -> caught and turned
  into a fallback `REFUSE` decision so the workflow can still complete the turn.
- Malformed/unparseable JSON from the model -> caught and turned the same as provider/network failure into a fallback
  `REFUSE` decision so the workflow can still complete the turn.

## Edge Cases

- Empty/blank `userMessage()` passed through to the prompt, and the model was unchanged. Classification of it is a model
  behaviour question, not a control-flow concern of this stage.
- `workflowPolicy().supportedCapabilities()` is empty still sent as an empty, comma-joined list to the prompt.

## Non-goals

- Does not itself decide EXECUTE_SCHEDULE vs EXECUTE. That is the workflow graph's responsibility.
- Does not validate or sanitize the scheduling fields it extracts (duration format, frequency limits). That validation
  happens downstream (e.g. in `CREATE_TASK`).
- Does not apply guardrails directly. Guard-railing happens inside the chat provider call.
