# Scheduled task execution (ScheduledAgentTaskRunner)

## Purpose

Execute one due occurrence of a conversation task using a system-triggered workflow and record the result.

## Requirements

- Eligibility must be checked against the task's current persisting state when the occurrence fires.
- A missing task or a task whose state is not `ACTIVE` must be skipped without starting an agent run or changing task
  storage.
- An eligible task must start one `SYSTEM_TRIGGER` workflow for the task's agent and conversation.
- The workflow message must be exactly `SCHEDULED: ` followed by the task instruction.
- Scheduled execution must not publish workflow progress to a user event stream.
- After the workflow trigger succeeds, the task must record the returned run identity and run metadata.
- After recording the run, an `ONCE` task must become `COMPLETED`.
- A `RECURRING` task must remain `ACTIVE` after a successful occurrence.

## Failure behaviour

- If a workflow triggering fails, the failure propagates and no run metadata or completion status may be recorded.
- If recording the run fails, the occurrence must be reported as failed even though the workflow has already run.
- An `ONCE` task must not be marked `COMPLETED` unless its run metadata was recorded successfully.

## Acceptance criteria

- Missing, paused, cancelled, and completed tasks are skipped without an agent run or storage mutation.
- An active one-time task starts one system-triggered run, records its run identity, and then becomes `COMPLETED`.
- An active recurring task starts one system-triggered run, records its run identity, and remains `ACTIVE`.
- A task paused or cancelled before its scheduled occurrence is checked again and skipped.
