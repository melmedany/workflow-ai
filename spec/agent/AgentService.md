# AgentService

## Purpose

Own the in-memory registry of runnable `Agent`s (built from persisted `AgentDefinition`s), and drive one workflow run
per `trigger` call while keeping `AgentRunTracker` and the SSE event-consumer registry consistent regardless of
whether the run succeeds or fails.

## Inputs / constraints / preconditions

- `trigger(AgentRequest, Consumer<WorkflowEvent>)` requires the request's `agentId` to resolve to an enabled agent
  (via `getAgent`), which is looked up/built before any run bookkeeping starts.

## Outputs / constraints / postconditions / invariants

- `trigger` always: starts a run (`AgentRunTracker.start`), registers the caller's event consumer for that run id,
  executes the agent's workflow, then either marks the run `complete` or `fail`s it, and — in a `finally` block —
  revokes the event consumer registration no matter which of those two outcomes happened.
- **Exception handling is currently uniform, not per-exception-type.** Any `Exception` thrown by `agent.execute(...)`
  is handled identically today: `agentRunTracker.fail(runId, ex.getMessage())` is called, then the *same* exception
  instance is rethrown unchanged (never wrapped, never swallowed). This works because, by the time an exception
  reaches this catch block, it has already been normalized upstream — `WorkflowExecutor.execute` converts every
  stage/graph failure into a `WorkflowExecutionResult`, and `Workflow.execute` converts a non-`COMPLETED` result into
  a single `WorkflowExecutionException` that already carries the agent id, the failing stage id (via
  `WorkflowStageException`), and the original message/cause. Callers that care about the specific failure inspect the
  rethrown exception's type/cause themselves. Whether to branch on exception type here instead is an open question,
  tracked by a `// TODO` on this catch block in the code — not something this spec resolves.
- `reload`/`remove`/`getEnabledAgent(s)`/`workflowDiagram` do not touch run tracking at all, they only affect the
  `agentsMap` registry or read from storage.

## Interfaces

- `UUID trigger(AgentRequest request, Consumer<WorkflowEvent> eventConsumer)`
- `Agent getEnabledAgent(UUID agentId)`
- `List<Agent> getEnabledAgents()`
- `void reload(UUID agentId)`
- `void remove(UUID agentId)`
- `String workflowDiagram(UUID agentId)`

## Acceptance criteria

- A successful run: exactly one `AgentRunTracker.start`, one `complete`, zero `fail` calls, and the consumer is
  revoked exactly once.
- A run whose `agent.execute(...)` throws any exception: exactly one `start`, zero `complete`, exactly one `fail`
  call with `ex.getMessage()`, the original exception propagates unchanged out of `trigger`, and the consumer is
  still revoked exactly once (via `finally`).
- `getAgent` failing (unknown/disabled agent) before the run starts -> no `AgentRunTracker` interaction at all, since
  the failure happens before `start` is called.

## Failure modes

- Any exception from `agent.execute(...)` (in practice, almost always `WorkflowExecutionException`/
  `WorkflowStageException`) is recorded via `agentRunTracker.fail` and rethrown as-is, never translated into a
  different exception type.
- An unknown/disabled agent id -> `AgentNotEnabledException`/lookup exception from `getAgent`, thrown before any run
  is started. Nothing to fail or revoke.

## Edge Cases

- `agentRunTracker.fail`/`complete` themselves throwing is not specially guarded against. Such a failure would
  propagate from the `catch`/try body normally, the `finally`'s consumer revocation still runs regardless.

## Non-goals

- Does not retry a failed run. A failed `trigger` call is terminal for that run id.
- Does not decide *how* a caller reacts to a failure (HTTP status, SSE error event, scheduler skip, etc.), only that
  the original exception is always available to whoever called `trigger`.