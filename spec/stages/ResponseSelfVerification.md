# Response self-verification (SelfVerificationStage)

## Purpose

Accept a valid draft immediately or make one corrective attempt for an invalid draft before final response persistence
and emission.

## Requirements

- A draft already marked valid must become the final response without another model request.
- An invalid draft that has not been retried must receive exactly one corrective generation attempt. The attempt must
  use the original message, invalid draft, validation reason, response contract, system context, and memory.
- The retry result must be validated against the same response contract.
- The retry result becomes final whether it passes validation. No second corrective generation is allowed.
- The final workflow state must mark validation as accepted. A performed retry must also be recorded.
- A self-verification started event must be emitted first.
- An already-valid draft or valid retry must emit a self-verification completed event.
- An invalid retry must emit a self-verification failed event containing the validation reason, but this event must not
  fail the overall turn.
- The stage outcome event must occur before the final response is persisted or emitted.
- The final response must be persisted as an `AGENT` message before any ordered token events and the response-completed
  event are emitted.

## Failure behaviour

- A provider failure during corrective generation terminates the stage and propagates to the workflow.
- A persistence failure must prevent token and response-completed emission.
- Validation failure after the single retry is recoverable. The latest response is returned as the best effort.

## Acceptance criteria

- A valid draft is persisted and emitted unchanged with no model retry and a completed stage event.
- An invalid draft that becomes valid uses one retry, persists that result, records the retry, and emits a completed
  stage event.
- An invalid draft whose retry is still invalid uses one retry, emits a failed stage event with the reason, persists the
  retry result, and still completes the turn.
- Every successful path persists the final response exactly once before emitting it.
