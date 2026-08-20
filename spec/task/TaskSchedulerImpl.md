# TaskSchedulerImpl (TaskScheduler)

## Purpose

Translate a `ConversationTask`'s schedule into JobRunr primitives: a one-off job for `ONCE` tasks, a cron-based
recurring job for `RECURRING` tasks, and pause/resume/cancel as add/remove operations against JobRunr.

## Inputs / constraints / preconditions

- Every method takes a fully-formed `ConversationTask`. The caller (`TaskService`) is responsible for what the task's
  fields mean in context (e.g. whether it's currently paused).
- `schedule`/`reschedule` read `task.schedule().parsedDuration()`, `task.schedule().startDateTime()`, `task.id()`,
  `task.agentId()`, `task.conversationId()`, and `task.createdAt()` (for `ONCE` tasks).
- `cancel`/`pause` read `task.runInfo().jobId()` and `task.runOnce()`.

## Outputs / constraints / postconditions / invariants

- **schedule**: rejects durations shorter than `PT1M` via `ScheduleTooFrequentException` before touching JobRunr.
  For a `ONCE` task, schedules a one-off job at `task.createdAt() + duration` (or `now + duration` if `createdAt` is
  not yet set, i.e. before the task has been persisted) keyed by `task.id()`. For a `RECURRING` task, builds a cron
  expression from `task.schedule().startDateTime()` and the parsed duration and registers a recurring job keyed by
  `task.id().toString()`. Because the job id is always derived from `task.id()`, the returned job id is stable across
  `schedule`/`reschedule` calls for the same task.
- **reschedule**: identical to `schedule` (re-registering under the same deterministic id replaces the prior
  registration in JobRunr).
- **pause**: delegates to `cancel` (removes the job from JobRunr, does not touch any persisted state).
- **resume**: delegates to `schedule` (re-registers the job from the task's current schedule fields).
- **cancel**: deletes the one-off job (`jobScheduler.delete`) for a `ONCE` task, or the recurring job
  (`jobScheduler.deleteRecurringJob`) for a `RECURRING` task, using `task.runInfo().jobId()`.
- **Cron anchoring**: the generated cron expression always anchors to `startDateTime`'s minute-of-hour
  (and hour-of-day, day-of-month/week, where applicable), not to "now" or to the top of the hour:
    - Sub-hour intervals (`< 60` minutes): the interval must evenly divide 60 (e.g. 1, 2, 3, 4, 5, 6, 10, 12, 15, 20,
      30). The cron minute field is the explicit, comma-separated list of every minute in `startDateTime`'s residue
      class mod the interval (e.g. start minute 7, every 15 minutes -> `"7,22,37,52"`), so the first-of-the-hour fire
      time matches the requested anchor instead of drifting to `:00/:15/:30/:45`. An interval that does not evenly
      divide 60 (e.g. every 7 minutes) throws `IllegalArgumentException`, since anchoring it would drift across hour
      boundaries every cycle — supporting that is explicitly out of scope.
    - Hour-multiple intervals (`< 24` hours, whole hours): anchored to `startDateTime`'s minute and hour, stepping by
      the given number of hours.
    - Exactly 24 hours: a single daily fire at `startDateTime`'s minute and hour.
    - Calendar periods (`Period`, not `Duration`): 1 day -> daily at the anchor's minute/hour, 7 days -> weekly on the
      anchor's day-of-week, N months (no day component) -> monthly on the anchor's day-of-month, every N months. Any
      other period (years, non-1/7 day counts, days combined with months) throws `IllegalArgumentException`.

## Interfaces

- `String schedule(ConversationTask task)`
- `String reschedule(ConversationTask task)`
- `void pause(ConversationTask task)`
- `void resume(ConversationTask task)`
- `void cancel(ConversationTask task)`

## Acceptance criteria

- A duration shorter than 1 minute -> `ScheduleTooFrequentException`, no JobRunr interaction.
- A `RECURRING` task with a 15-minute duration and a start time at minute 7 produces cron minute field
  `"7,22,37,52"`.
- A `RECURRING` task with a 7-minute duration -> `IllegalArgumentException` (60 is not evenly divisible by 7).
- A `RECURRING` task with an hour-multiple, day, week, or supported month interval produces the corresponding cron
  expression anchored to `startDateTime`'s minute/hour/day-of-week/day-of-month as described above.
- `reschedule` re-registers under the same job id as the original `schedule` call for the same task id.
- `cancel` on a `ONCE` task calls `jobScheduler.delete` with the task's job id parsed as a `UUID`. On a `RECURRING`
  task calls `jobScheduler.deleteRecurringJob` with the raw job id string.

## Failure modes

- Any duration/period shape not covered above (sub-hour interval not dividing 60, non-whole-hour duration over an
  hour, unsupported period shape) -> `IllegalArgumentException`, not silently rounded or approximated.
- `cancel`/`pause` on a job id that no longer exists in JobRunr -> JobRunr's own `JobNotFoundException`/
  `IllegalJobStateChangeException` propagate uncaught. `TaskService` decides how to react (see `TaskService.md`'s
  consistency-strategy note).

## Edge Cases

- A `ONCE` task resumed after its originally computed fire time has already elapsed schedules into the past.
  JobRunr's own past-due handling (firing as soon as it's next polled) governs what happens next, this class does not
  special-case it.

## Non-goals

- Does not decide *whether* a task should currently have a live job (e.g. because it's paused) — that decision, and
  keeping storage/scheduler in sync when one side fails, belongs to `TaskService`.
- Does not support arbitrary/irregular recurrence patterns beyond the shapes enumerated above.