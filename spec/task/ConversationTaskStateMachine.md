# ConversationTask / TaskStatus state machine

## Purpose

Define the lifecycle of a standing scheduled task (`ConversationTask.schedule().status()`) as it is created, run,
paused/resumed, and eventually cancelled or completed.

## Inputs / constraints / preconditions

- States: `TaskStatus.ACTIVE`, `PAUSED`, `COMPLETED`, `CANCELLED`.
- A task also carries a `TaskSchedule.ScheduleType` (`ONCE` or `RECURRING`), which determines whether it can ever
  reach `COMPLETED` on its own.

## Outputs / constraints / postconditions / invariants

- A newly created task (`TaskService.create`) always starts in `ACTIVE`.
- `TaskService.pause` transitions a task to `PAUSED`.
- `TaskService.resume` transitions a task to `ACTIVE`.
- `TaskService.cancel` (and `cancelAll`, which cancel per task) transitions a task to `CANCELLED`.
- `ScheduledAgentTaskRunner.run`, after successfully firing a `ScheduleType.ONCE` task, transitions it to
  `COMPLETED`. A `RECURRING` task never transitions to `COMPLETED` automatically. It stays `ACTIVE` (or whatever
  status it had) until explicitly cancelled.
- Updating a task's instruction/schedule (`TaskService.createOrUpdate` matching an existing intent) does NOT change
  its status. `ConversationTask.update(...)` preserves `schedule.status()` as-is. A `PAUSED` task that gets a
  matching-intent update request stays `PAUSED`.
- `CANCELLED` and `COMPLETED` are intended as terminal: nothing in `TaskService` transitions a task out of either.
  However, `TaskService` itself does not enforce this. Whether a `CANCELLED`/`COMPLETED` task can still be found
  and re-paused/resumed depends entirely on what `ConversationTaskStorage.findTaskWithStatus`.

## Interfaces

- `TaskStatus` enum: `ACTIVE`, `PAUSED`, `COMPLETED`, `CANCELLED`.
- `ConversationTask.withStatus(TaskStatus)`, `runOnce()`, `nextRunAt()`.

## Acceptance criteria

- New task status is `ACTIVE`.
- `pause` -> `PAUSED`, `resume` -> `ACTIVE`, `cancel` -> `CANCELLED`.
- A `ONCE` task's status becomes `COMPLETED` after its scheduled run fires.
- A `RECURRING` task's status is unaffected by firing its scheduled run (stays whatever it was, typically `ACTIVE`).
- Editing an existing task's instruction/schedule never changes its current status.

## Edge Cases

- Calling `resume` on a task that is already `ACTIVE`, or `pause` on one already `PAUSED`, is not explicitly guarded
  against by `TaskService`. Whether it's reachable at all depends on `findTaskWithStatus`'s definition of "active".

## Non-goals

- Does not define what `ConversationTaskStorage.findTaskWithStatus` returns for non-`ACTIVE` statuses. That is an
  implementation detail of the storage adapter.
