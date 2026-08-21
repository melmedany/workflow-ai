# Trigger message persistence (PersistUserMessageStage)

## Purpose

Record the message that started a workflow run with a role that identifies whether it came from a user or the system.

## Requirements

- Exactly one trigger message must be stored for each run that reaches this stage.
- A `SYSTEM_TRIGGER` message must be stored with role `SYSTEM`.
- A `USER_MESSAGE`, including a run with no explicit trigger source, must be stored with role `USER`.
- Message content must be stored exactly as received, including empty or whitespace-only content.
- A persistence-started event must be emitted before storage and a persistence-completed event after successful storage.
- This stage must not change workflow routing or response state.

## Failure behaviour

- A storage failure terminates the stage and propagates to the workflow.
- The completed event must not be emitted when storage fails.

## Acceptance criteria

- User and system triggers are stored once with `USER` and `SYSTEM` roles respectively.
- Stored content exactly matches the triggering message.
- Successful storage is bracketed by one-started and one-completed event in that order.
- Failed storage emits no completion event.
