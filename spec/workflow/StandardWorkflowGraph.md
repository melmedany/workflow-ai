# STANDARD workflow graph (WorkflowExecutorFactory)

## Purpose

Wire the twelve stages into the single execution graph for `WorkflowId.STANDARD`, routing each turn to the right branch
based on trigger source and classification decision, while guaranteeing every branch eventually reaches
`COMPLETE`.

## Inputs / constraints / preconditions

- Built once per `WorkflowId.STANDARD` request via `WorkflowExecutorFactory.build(WorkflowId.STANDARD)`. Every
  `StageId` referenced in the graph must have a corresponding `WorkflowStage` supplied to the factory, or
  `stages.get(...)` yields `null` and node registration fails at build time (`WorkflowBuildException`).
- Routing decisions are made by reading `WorkflowState.triggerSource()` (after `LOAD_MEMORY`) and
  `WorkflowState.routingDecision()`/`schedulingRequested()` (after `CLASSIFICATION`) and, again,
  `WorkflowState.routingDecision()` after `CREATE_TASK`.

## Outputs / constraints / postconditions / invariants

- Full routing table:
    - `START -> PERSIST_USER_MESSAGE -> LOAD_MEMORY` (always, regardless of trigger source).
    - `LOAD_MEMORY -> CLASSIFICATION` when `triggerSource() == USER_MESSAGE`, `LOAD_MEMORY -> EXECUTE_WORKFLOW` when
      `triggerSource() == SYSTEM_TRIGGER` (scheduled runs skip classification entirely).
    - `CLASSIFICATION -> EXECUTE_WORKFLOW` for decision mode `EXECUTE` without `schedulingRequested()`.
    - `CLASSIFICATION -> CREATE_TASK` for decision mode `EXECUTE` WITH `schedulingRequested()` (the synthetic
      `EXECUTE_SCHEDULE` routing target, never a real `DecisionMode` value stored on the decision itself).
    - `CLASSIFICATION -> GENERATE_CLARIFICATION` for `CLARIFY`, `-> GENERATE_GREETING` for `GREET`, `->
    GENERATE_REDIRECT` for `REDIRECT`, `-> GENERATE_REFUSAL` for `REFUSE` (including when `routingDecision()` is absent,
      defaulted to `REFUSE`).
    - `EXECUTE_WORKFLOW -> SELF_VERIFICATION -> COMPACT_MEMORY` (always, unconditional).
    - `CREATE_TASK -> COMPACT_MEMORY` when its resulting decision mode is `EXECUTE` (task created/updated, or no
      decision override), `-> GENERATE_CLARIFICATION` for `CLARIFY`, `-> GENERATE_REFUSAL` for `REFUSE`.
    - `GENERATE_CLARIFICATION -> COMPACT_MEMORY`, `GENERATE_GREETING -> COMPACT_MEMORY`, `GENERATE_REDIRECT ->
    COMPACT_MEMORY`, `GENERATE_REFUSAL -> COMPACT_MEMORY` (always, unconditional).
    - `COMPACT_MEMORY -> COMPLETE -> END` (always, unconditional).
- Invariant: every reachable path terminates at `COMPLETE` and then `END`. This holds as a structural fact of the graph
  wiring. However, it is also contingent on `CreateTaskStage` only ever producing a decision whose mode is one of
  `EXECUTE`, `CLARIFY`, or `REFUSE` after `CREATE_TASK` runs (per its own spec), if it ever produced `GREET` or
  `REDIRECT`, `addConditionalEdges` would have no matching target and the graph would fail at runtime for that turn.
- A stage exception (other than `WorkflowStageException`) thrown inside `asyncNode` is wrapped into a
  `WorkflowStageException` carrying the agent id, failing `StageId`, and original message. A `WorkflowStageException`
  thrown by a stage is passed through unwrapped. Either way, no path silently swallows a stage exception, it propagates
  out of `graph.invoke(...)` and is reported by `WorkflowExecutor.execute` as a failed
  `WorkflowExecutionResult`.

## Interfaces

- `WorkflowExecutorFactory(List<WorkflowStage> stages)`
- `boolean isSupported(WorkflowId workflowId)`
- `WorkflowExecutor build(WorkflowId workflowId)`

## Acceptance criteria

- A `USER_MESSAGE`-triggered turn classified as `EXECUTE` (no scheduling) visits, in order: `PERSIST_USER_MESSAGE`,
  `LOAD_MEMORY`, `CLASSIFICATION`, `EXECUTE_WORKFLOW`, `SELF_VERIFICATION`, `COMPACT_MEMORY`, `COMPLETE`.
- A `USER_MESSAGE`-triggered turn classified as `EXECUTE` WITH `schedulingRequested()` visits `CREATE_TASK` instead of
  `EXECUTE_WORKFLOW`, then `COMPACT_MEMORY`, `COMPLETE` (assuming `CREATE_TASK` keeps the decision at `EXECUTE`).
- A turn classified as each of `CLARIFY`, `GREET`, `REDIRECT`, `REFUSE` visits the matching `GENERATE_*` stage, then
  `COMPACT_MEMORY`, `COMPLETE`.
- A `SYSTEM_TRIGGER` turn skips `CLASSIFICATION` entirely and goes straight from `LOAD_MEMORY` to
  `EXECUTE_WORKFLOW`.
- A `CREATE_TASK` outcome of `CLARIFY` or `REFUSE` routes to `GENERATE_CLARIFICATION`/`GENERATE_REFUSAL`
  respectively instead of `COMPACT_MEMORY` directly.
- `isSupported` returns `true` only for `WorkflowId.STANDARD`.
- `build` on an unsupported `WorkflowId` throws `WorkflowBuildException`.

## Failure modes

- Missing a `WorkflowStage` implementation for a `StageId` referenced by the graph -> `WorkflowBuildException` at build
  time (`stateGraph.addNode` receives `null`).
- An unmapped edge-condition string reaching `addConditionalEdges` (e.g. `CreateTaskStage` returning a decision mode
  outside `{EXECUTE, CLARIFY, REFUSE}`) -> a runtime graph-navigation failure for that turn, surfaced as a failed
  `WorkflowExecutionResult`, not silently ignored.

## Edge Cases

- `routingDecision()` absent after `CLASSIFICATION` (should not normally happen, since `CLASSIFICATION` always sets one)
  defaults the edge condition to `REFUSE`, so the turn still completes via `GENERATE_REFUSAL`.

## Non-goals

- Does not itself decide *what* each stage does, only the sequencing/branching between stages.
- Does not handle workflow variants other than `STANDARD` (only `STANDARD` is in `SUPPORTED`).
