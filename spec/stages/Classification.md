# Classification (ClassificationStage)

## Purpose

Choose exactly one routing mode for a user request and, when scheduling was requested, extract the schedule details
needed to create a task.

## Requirements

- Classification must consider the user message, available memory, the agent policy, supported capabilities, and
  whether scheduling was requested.
- The result must always be a non-null routing decision with one of these modes: `GREET`, `EXECUTE`, `CLARIFY`,
  `REDIRECT`, or `REFUSE`.
- Classification never returns `EXECUTE_SCHEDULE`. The workflow derives the scheduling branch from `EXECUTE` together
  with the scheduling-request flag.
- When scheduling was not requested, schedule fields in the decision must be absent.
- When scheduling was requested and the request can be executed as a task, the decision must include schedule type,
  start time, duration, and task instruction for downstream validation.
- A classification-started event must precede classification. A classification-completed event and then a decision
  event must follow every normal or fallback result.

## Failure behaviour

- A model, provider, guardrail, or response-parsing failure must become a `REFUSE` decision rather than failing the
  workflow.
- The fallback reason must begin with `Classification unavailable: ` and include the underlying failure message.
- The fallback decision's extracted intent must be the original user message.
- Invalid schedule details are not repaired during classification. They are handled by task creation.

## Acceptance criteria

- A valid classification result is returned unchanged as the workflow's routing decision.
- A provider failure and malformed classification output each produce the defined fallback refusal.
- Exactly one started, completed, and decision event is emitted in that order for each classification.
- Scheduling fields are absent when scheduling is off and available for a schedulable decision when scheduling is on.
