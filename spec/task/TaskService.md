# TaskService

## Purpose

Create, update (by deduplicating on intent), pause, resume, cancel, and list standing scheduled tasks for a
conversation, keeping `ConversationTaskStorage` and `TaskScheduler` in sync.

## Inputs / constraints / preconditions

- `createOrUpdate(agentId, conversationId, instruction, scheduleType, startDateTime, duration)`, `instruction` must be
  non-null (used to derive the dedup key).
- `pause`/`resume`/`cancel(agentId, conversationId, taskId)` require the task to be found via
  `ConversationTaskStorage.findTask`.
  `cancel(agentId, conversationId, taskId)/cancelAll(agentId, conversationId)` requires the task to be found via
  `ConversationTaskStorage.findTask`.

## Outputs / constraints / postconditions / invariants

- **Dedup key**: `generateIntentKey(instruction)` = hex MD5 (Spring's `DigestUtils.md5DigestAsHex`) of
  `instruction.trim().toLowerCase(Locale.ROOT)`. Two instructions that differ only by case or leading/trailing
  whitespace produce the same key. Any other difference produces a different key.
- **createOrUpdate for no existing tasks for the conversation**: always creates a new task.
- **createOrUpdate for existing tasks, none with a matching `intentKey`**: creates a new, additional task. Existing
  tasks are untouched.
- **createOrUpdate for an existing task with a matching `intentKey`**: updates that task's instruction/schedule in place
  (same task id) instead of creating a second one. Its `schedule.status()` is preserved unchanged.
- **create**: builds a new task with `TaskDefinition(instruction, intentKey, instruction)` (instruction is used as both
  the initial display name and the content) and `TaskSchedule(scheduleType, startDateTime, duration, ACTIVE)`. Asks
  `TaskScheduler.schedule` for a job id first, then persists the task once via `ConversationTaskStorage.create` with
  that job id already attached, the returned task carries the job id.
- **update**: only when the existing task's `schedule.status()` is `ACTIVE`, asks `TaskScheduler.reschedule` for a
  (possibly new) job id first. Otherwise (`PAUSED`/`CANCELLED`/`COMPLETED`) the scheduler is not called and the
  existing job id is kept as-is, since there is no live job to reschedule. Either way, persists the updated
  instruction/schedule once via `ConversationTaskStorage.update`, the returned task carries the previous status
  unchanged.
- **pause**: looks up the task via `findTaskWithStatus` (else `TaskNotFoundException`), sets its stored status to
  `PAUSED`, and calls `TaskScheduler.pause`.
- **resume**: looks up the task via `findTaskWithStatus` (else `TaskNotFoundException`), sets its stored status to
  `ACTIVE`, and calls `TaskScheduler.resume`.
- **cancel**: looks up the task via `findTaskWithStatus` (else `TaskNotFoundException`), sets its stored status to
  `CANCELLED`, and calls `TaskScheduler.cancel`.
- **cancelAll**: cancels every task returned by `findByConversation` for the conversation, one at a time. A single
  task's `cancel` failing with `JobNotFoundException` or `TaskNotFoundException` is logged and does NOT stop the
  remaining tasks from being cancelled, this is a best-effort bulk operation, not all-or-nothing.
- **listByConversation**: a pure passthrough to `ConversationTaskStorage.findByConversation`.

## Interfaces

-

`ConversationTask createOrUpdate(UUID agentId, UUID conversationId, String instruction, ScheduleType scheduleType, Instant startDateTime, String duration)`

- `void pause(UUID agentId, UUID conversationId, UUID taskId)`
- `void resume(UUID agentId, UUID conversationId, UUID taskId)`
- `void cancel(UUID agentId, UUID conversationId, UUID taskId)`
- `void cancelAll(UUID agentId, UUID conversationId)`
- `List<ConversationTask> listByConversation(UUID agentId, UUID conversationId)`

## Acceptance criteria

- The same instruction text with different case/whitespace routes to the same task on a second `createOrUpdate` call
  (update, not a duplicate creation).
- A materially different instruction on a second `createOrUpdate` call for the same conversation creates a second,
  independent task.
- `create` results in exactly one `TaskScheduler.schedule` call followed by exactly one
  `ConversationTaskStorage.create` call. The returned task's job id matches the scheduler's result.
- `update` (matching intent) on a currently-`ACTIVE` task results in exactly one `TaskScheduler.reschedule` call and
  the task keeps its pre-existing status.
- `update` (matching intent) on a currently-`PAUSED` (or `CANCELLED`/`COMPLETED`) task results in ZERO scheduler
  calls, only the instruction/schedule fields change in storage, and the task keeps its pre-existing status and job
  id. This is what prevents editing a paused task's schedule from silently reactivating its job.
- `pause`/`resume`/`cancel` on an unknown task id throw `TaskNotFoundException` and never call the scheduler.
- `cancelAll` with 3 tasks where the 2nd throws `JobNotFoundException` during cancellation still attempts (and succeeds
  on) the 1st and 3rd.
- `listByConversation` returns exactly what storage returns, unmodified.

## Failure modes

- `pause`/`resume`/`cancel` on a task id not found by `findTaskWithStatus` -> `TaskNotFoundException`, no storage/
  scheduler side effects.
- `cancelAll`'s per-task `JobNotFoundException`/`TaskNotFoundException` are caught and logged. any other exception type
  from `cancel` propagates and aborts the remaining loop iterations.
- MD5 being unavailable (should never happen on a standard JVM. Spring's `DigestUtils` wraps a hypothetical
  `NoSuchAlgorithmException` in an `IllegalStateException` internally) is not a business-logic outcome.
- **Database <-> scheduler consistency strategy**: every mutating method performs its `TaskScheduler` side effect
  first and its `ConversationTaskStorage` write second, and logs (`log.error`) then attempts one best-effort
  compensating scheduler call inline in its `catch (RuntimeException ex)` block before rethrowing `ex` unchanged.
  This is a deliberate, best-effort strategy, not a distributed transaction:
    - `create` -> `scheduler.cancel(scheduledTask)`.
    - `update` -> `scheduler.reschedule(existingTask)`, but only when `reschedule` was `true` on the forward path
      (i.e. only for a task that was `ACTIVE`). Updating a `PAUSED`/`CANCELLED`/`COMPLETED` task never touches the
      scheduler, forward or compensating, even if `storage.update` fails.
    - `pause` -> `scheduler.resume(task)`, `resume` -> `scheduler.pause(task)`.
    - `cancel(ConversationTask)` (shared by both `cancel(agentId, conversationId, taskId)` and `cancelAll`) wraps
      *both* its `scheduler.cancel(task)` call and its `storage.updateStatus(..., CANCELLED)` call in the same
      try/catch, so a failure from either step triggers the same compensation: `scheduler.schedule(task)`
      (re-registers the job).
  The caller never sees the operation reported as successful when it wasn't. This is not proof against a second,
  simultaneous failure: if the compensating scheduler calls itself throws, that new exception propagates *instead
  of* the original storage/scheduler failure that triggered it (there is no nested catch guarding the compensation
  attempt) — a deliberate simplification for the common single-failure case, not a full saga/outbox mechanism for a
  rare double failure.

## Edge Cases

- `instruction` differing only by internal (not leading/trailing) whitespace or punctuation produces a different
  `intentKey` (only `trim()` + case-folding is applied, internal whitespace is significant).
- A conversation with tasks whose `intentKey` values collide only coincidentally (MD5 collision) is not something
  this service defends against, treated as a non-concern.

## Non-goals

- Does not decide the actual cron/scheduling mechanics, delegated entirely to `TaskScheduler`.
- Does not validate the schedule fields (duration format, frequency limits), that happens in
  `TaskScheduler`/`CreateTaskStage` before/around this service.
