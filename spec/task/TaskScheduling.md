# Task scheduling (TaskScheduler)

## Purpose

Define how one-time and recurring conversation task schedules become executable jobs.

Define the states and valid transitions of `ONCE` and `RECURRING` conversation tasks.

## Requirements

- `ONCE` schedules create one execution at the task creation time plus the requested duration. If creation time has not
  yet been assigned, scheduling time is used as the base.
- `RECURRING` schedules create repeated executions anchored to the requested start date and time.
- The scheduling identity must be stable for a task across schedule and reschedule operations.
- Rescheduling replaces the prior registration for that task.
- Pausing and cancelling remove the registered job without changing the persisted task state.
- Resuming registers the job again from the task's current schedule.
- No interval shorter than one minute is allowed.

## States

- `ACTIVE` means the task is eligible to run.
- `PAUSED` means the task is retained but is not eligible to run.
- `COMPLETED` means a one-time task has run successfully.
- `CANCELLED` means the task has been explicitly ended.

## Transition rules

- Every new task starts as `ACTIVE`.
- Only an `ACTIVE` task can be paused. A successful pause changes it to `PAUSED`.
- Only a `PAUSED` task can be resumed. A successful resume changes it to `ACTIVE`.
- Cancellation changes an existing task to `CANCELLED`.
- A successful scheduled run changes an `ONCE` task from `ACTIVE` to `COMPLETED`.
- Running a `RECURRING` task does not change its status. It remains `ACTIVE` for future occurrences.
- Updating a task's instruction or schedule preserves its current status.
- `COMPLETED` and `CANCELLED` tasks cannot be paused or resumed.

## Supported recurring intervals

- A sub-hour interval is supported only when its whole-minute value divides 60 evenly. Occurrences retain the start
  time's minute offset. For example, every 15 minutes starting at minute 7 runs at minutes 7, 22, 37, and 52.
- A whole-hour interval shorter than 24 hours retains the start time's minute and hour offset.
- A 24-hour interval runs daily at the start time's minute and hour.
- A calendar period of one day runs daily at the start time's minute and hour.
- A calendar period of seven days runs weekly on the start time's day of week, hour, and minute.
- A period of one or more whole months with no day component runs on the start time's day of month, hour, and minute at
  that month interval.
- Other interval shapes are unsupported and must not be rounded or approximated.

## Failure behaviour

- An interval shorter than one minute must fail before a job is registered.
- An unsupported interval, including a sub-hour interval that does not divide 60, must fail explicitly.
- Pausing or cancelling a job that the scheduler cannot remove must report the scheduler failure.
- Resuming a one-time task whose computed time is already past does not adjust the schedule. The scheduler's overdue-job
  policy determines when it runs.

## Acceptance criteria

- Task creation produces `ACTIVE`.
- Task pause and resume produce `ACTIVE -> PAUSED -> ACTIVE`.
- Task cancellation produces `CANCELLED`.
- A successful one-time occurrence produces `COMPLETED`.
- A successful recurring occurrence remains `ACTIVE`.
- Editing a paused task leaves it paused and does not make it eligible to run.
- Supported hourly, daily, weekly, and monthly intervals preserve their documented anchor fields.
- Scheduling and rescheduling the same task use the same scheduling identity.
- Pause and cancel remove the correct job type, while resume registers it again from current task data.
