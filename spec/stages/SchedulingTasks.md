# Scheduled task creation `(CreateTaskStage)

## Purpose

Turn a scheduling decision into a standing conversation task, confirm the task to the user, or return a precise
clarification or refusal outcome.

## Requirements

- This stage requires a routing decision. Reaching it without one is a workflow-contract failure.
- A system-triggered run must never create or update another scheduled task. It must return `REFUSE` with reason
  `A scheduled run cannot create another scheduled task` and use the triggering message as the extracted intent.
- The task instruction must have scheduling command language removed before task creation.
- If no non-blank instruction remains, no task may be created. The result must be `REFUSE` with reason
  `Task instruction cannot be extracted or schedule other tasks` and preserve the decision's extracted intent.
- A valid schedule must create or update a task using the cleaned instruction, schedule type, parsed start time, and
  duration.
- Success must produce a user-facing confirmation that describes the task instruction. The confirmation must be
  persisted before any of its response tokens or response-completed event is emitted.
- Success must retain an `EXECUTE` decision and mark the generated response as accepted.
- An invalid, missing, or unparsable schedule must return `CLARIFY`, preserve the extracted intent, include the
  underlying reason, and ask the user to restate the schedule.
- A schedule rejected for running too frequently must return `REFUSE`, preserving the policy failure as its reason.
- A task-stage started event must be emitted first. Every handled outcome must then emit one task-stage completed event.

## Failure behaviour

- Missing routing state must fail the workflow rather than create a task from incomplete data.
- Schedule syntax and validation failures are recoverable through `CLARIFY`.
- The minimum-frequency policy is a terminal refusal for that request, not a workflow failure.
- Unexpected task-service failures propagate and must not be presented as successful task creation.

## Acceptance criteria

- A system trigger and an empty cleaned instruction each produce the specified refusal with no task mutation.
- Valid `ONCE` and `RECURRING` schedules create or update the task with the exact cleaned instruction and parsed
  schedule fields, then persist and emit a confirmation.
- Missing or invalid schedule fields produce a clarification question rather than an unhandled failure.
- A too-frequent schedule produces a refusal containing the policy reason.
- Every handled result emits one started event followed by one completed event.
