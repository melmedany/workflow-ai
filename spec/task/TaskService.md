# TaskService

## Purpose

Create, update (by deduplicating on intent), pause, resume, cancel, and list standing scheduled tasks for a
conversation, keeping `ConversationTaskStorage` and `TaskScheduler` in sync.

## Inputs / constraints / preconditions

- `createOrUpdate(agentId, conversationId, instruction, scheduleType, startDateTime, duration)`, `instruction` must be
  non-null (used to derive the dedup key).
- `pause`/`resume`/`cancel(agentId, conversationId, taskId)` require the task to be found via
  `ConversationTaskStorage.findTaskWithStatus`.
  `cancel(agentId, conversationId, taskId)/cancelAll(agentId, conversationId)` requires the task to be found via
  `ConversationTaskStorage.findTask`.

## Outputs / constraints / postconditions / invariants

- **Dedup key**: `intentKey(instruction)` = hex SHA-256 of `instruction.trim().toLowerCase(Locale.ROOT)`. Two
  instructions that differ only by case or leading/trailing whitespace produce the same key. Any other difference
  produces a different key.
- **createOrUpdate for no existing tasks for the conversation**: always creates a new task.
- **createOrUpdate for existing tasks, none with a matching `intentKey`**: creates a new, additional task. Existing
  tasks are untouched.
- **createOrUpdate for an existing task with a matching `intentKey`**: updates that task's instruction/schedule in place
  (same task id) instead of creating a second one. Its `schedule.status()` is preserved unchanged.
- **create**: builds a new task with `TaskDefinition(instruction, intentKey, instruction)` (instruction is used as both
  the initial display name and the content) and `TaskSchedule(scheduleType, startDateTime, duration, ACTIVE)`. Persists
  it, asks `TaskScheduler.schedule` for a job id, and persists that job id, the returned task carries the job id.
- **update**: persists the updated instruction/schedule, asks `TaskScheduler.reschedule` for a (possibly new) job id,
  and persists that job id, the returned task carries the new job id and the previous status.
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
- `create` results in exactly one `ConversationTaskStorage.create`, one `TaskScheduler.schedule`, and one
  `updateJobId` call. The returned task's job id matches the scheduler's result.
- `update` (matching intent) results in exactly one `TaskScheduler.reschedule` call and the task keeps its pre-existing
  status.
- `pause`/`resume`/`cancel` on an unknown task id throw `TaskNotFoundException` and never call the scheduler.
- `cancelAll` with 3 tasks where the 2nd throws `JobNotFoundException` during cancellation still attempts (and succeeds
  on) the 1st and 3rd.
- `listByConversation` returns exactly what storage returns, unmodified.

## Failure modes

- `pause`/`resume`/`cancel` on a task id not found by `findTaskWithStatus` → `TaskNotFoundException`, no storage/
  scheduler side effects.
- `cancelAll`'s per-task `JobNotFoundException`/`TaskNotFoundException` are caught and logged. any other exception type
  from `cancel` propagates and aborts the remaining loop iterations.
- SHA-256 being unavailable (should never happen on a standard JVM) → `IllegalStateException`, not a business-logic
  outcome.

## Edge Cases

- `instruction` differing only by internal (not leading/trailing) whitespace or punctuation produces a different
  `intentKey` (only `trim()` + case-folding is applied, internal whitespace is significant).
- A conversation with tasks whose `intentKey` values collide only coincidentally (SHA-256 collision) is not something
  this service defends against, treated as a non-concern.

## Non-goals

- Does not decide the actual cron/scheduling mechanics, delegated entirely to `TaskScheduler`.
- Does not validate the schedule fields (duration format, frequency limits), that happens in
  `TaskScheduler`/`CreateTaskStage` before/around this service.
