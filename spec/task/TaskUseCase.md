# Conversation task management (TaskUseCase)

## Purpose

Create, update, pause, resume, cancel, and list scheduled tasks while keeping the stored task state and scheduler state
as consistent as possible when an operation fails.

## Creation and update

- Instructions are compared after removing leading and trailing whitespace and applying locale-independent lowercase.
  Internal whitespace and punctuation remain significant.
- Within one conversation, an instruction matching an existing task under that normalization updates the existing task
  instead of creating a duplicate. The task identity and current status are preserved.
- An instruction with no match creates an additional task without changing existing tasks.
- A new task uses the instruction as both its initial display name and its instruction content, and starts as `ACTIVE`.
- New tasks must be scheduled before they are persisted, and the persisted task must contain the scheduler identity.
- Updating an `ACTIVE` task must replace its scheduled job before persisting the new instruction and schedule.
- Updating a `PAUSED`, `CANCELLED`, or `COMPLETED` task changes stored instruction and schedule fields without
  registering a job. Its status and existing scheduler identity are preserved.
- Listing tasks returns all tasks for the requested agent and conversation without changing them.

## State operations

- Pausing requires an `ACTIVE` task, removes its scheduled job, and then stores `PAUSED`.
- Resuming requires a `PAUSED` task, registers its current schedule, and then stores `ACTIVE`.
- Cancelling requires an existing task, removes its scheduled job, and then stores `CANCELLED`.
- Cancelling all tasks is the best effort. Failure to find or remove one task or job must not prevent cancellation
  attempts for the remaining tasks.

## Failure and consistency behaviour

- If a required task cannot be found in the state needed for pause or resume, the operation must fail without a
  scheduler or storage change.
- If persistence fails after scheduling a new task, one attempt must be made to remove the new job before the original
  operation is reported as failed.
- If persistence fails after rescheduling an active task, one attempt must be made to restore its prior schedule.
- If storing `PAUSED` fails, one attempt must be made to restore the job. If storing `ACTIVE` fails, one attempt must be
  made to remove the newly restored job.
- If cancellation fails during either job removal or status persistence, one attempt must be made to restore the prior
  scheduled job.
- These recovery actions are the best effort and do not provide a distributed transaction. A recovery failure is allowed
  to propagate.
- During cancel-all, missing-task and missing-job failures are isolated per task. Other failures terminate the bulk
  operation.

## Acceptance criteria

- Instructions that differ only by case or surrounding whitespace refer to the same task. A material text difference
  creates a separate task.
- Creating a task registers one job and persists one active task carrying that job identity.
- Updating an active task reschedules it and preserves its identity and status.
- Updating a non-active task changes stored task details without registering or changing a job.
- Pause, resume, and cancel enforce their required source states and produce the lifecycle defined in
  [TaskScheduling.md](TaskScheduling.md).
- Cancel-all continues after one missing task or job and still attempts every remaining task.
- A failed mutation is reported as failed and triggers the corresponding single recovery attempt.
