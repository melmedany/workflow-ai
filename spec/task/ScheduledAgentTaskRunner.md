# ScheduledAgentTaskRunner

## Purpose

Fire a single scheduled occurrence of a `ConversationTask`: skip it if it's no longer eligible to run, otherwise trigger
the agent workflow for it (`SYSTEM_TRIGGER`) and record the outcome, completing `ONCE` tasks after their one run.

## Inputs / constraints / preconditions

- `run(agentId, conversationId, taskId)` is the entry point JobRunr calls (via `TaskSchedulerImpl`) when a job's
  scheduled/cron time arrives.
- Depends on `ConversationTaskStorage.findTaskWithStatus` to fetch the current task state at fire time (not whatever state
  it had when originally scheduled), and on `AgentUseCase.trigger` to actually run the agent workflow.

## Outputs / constraints / postconditions / invariants

- If the task cannot be found by `findTaskWithStatus`, OR its current `schedule().status()` is anything other than
  `ACTIVE` (e.g. `PAUSED`, `CANCELLED`, `COMPLETED`), the run is skipped entirely: no `AgentUseCase.trigger` call, no
  storage writes. This is the mechanism by which pausing/cancelling a task actually prevents a job that's already
  scheduled in JobRunr from doing anything when it fires.
- Otherwise, triggers the agent workflow with `AgentRequest.systemTrigger(task.agentId(), task.conversationId(),
  task.id(), "SCHEDULED: " + instruction)` and a no-op event consumer. This run is fire-and-forget from the scheduler's
  perspective. It does not stream events anywhere.
- After a successful trigger, `ConversationTaskStorage.updateAfterRun` is always called with the new run id.
- If the task is `ONCE` (`runOnce()`), its status is additionally set to `COMPLETED` after `updateAfterRun`. A
  `ONCE` task never fires a second time.
- If the task is `RECURRING`, its status is left untouched (stays `ACTIVE`) so it can fire again on its next schedule.
- Returns `void`. There is no way for a caller to observe the success / failure of the triggered workflow run from this
  method's return value.

## Interfaces

- `void run(UUID agentId, UUID conversationId, UUID taskId)`

## Acceptance criteria

- Task not found → zero interactions with `AgentUseCase` or `ConversationTaskStorage.updateAfterRun`/`updateStatus`.
- Task found but `PAUSED`/`CANCELLED`/`COMPLETED` → zero interactions with `AgentUseCase` or those storage methods.
- Task found and `ACTIVE`, `ONCE` schedule → exactly one `AgentUseCase.trigger` call with a `SYSTEM_TRIGGER` request
  referencing the task's own ids and an instruction-derived message, followed by exactly one `updateAfterRun` and
  exactly one `updateStatus(..., COMPLETED)`.
- Task found and `ACTIVE`, `RECURRING` schedule → exactly one `trigger` call and exactly one `updateAfterRun`, and zero
  `updateStatus` calls.
- The triggered `AgentRequest`'s message is exactly `"SCHEDULED: " + task.definition().instruction()`.

## Failure modes

- If `AgentUseCase.trigger` throws, the exception propagates uncaught. `updateAfterRun`/`updateStatus` are never reached
  for that run (no partial/inconsistent storage update is attempted after a failed trigger).

## Edge Cases

- A task that transitions to `PAUSED`/`CANCELLED` between being scheduled and the job actually firing is correctly
  skipped, because eligibility is re-checked live via `findTaskWithStatus` at fire time, not decided once at schedule time.

## Non-goals

- Does not reschedule or cancel the underlying JobRunr job itself. That's `TaskSchedulerImpl`'s job (a `RECURRING`
  job keeps firing on its cron schedule regardless of this method's outcome, until explicitly cancelled via
  `TaskService`/`TaskScheduler`).
- Does not stream workflow progress events anywhere. The event consumer passed to `trigger` is a no-op.
