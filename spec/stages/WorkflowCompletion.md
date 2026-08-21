# Workflow completion (CompletionStage)

## Purpose

Signal successful conversation completion and send the final agent response through every registered notification
channel.

## Requirements

- Every successful workflow path must reach completion exactly once.
- Completion must emit one `CONVERSATION_COMPLETED` event for each registered workflow event destination.
- `COMPLETE` must not emit stage-started or stage-completed progress events.
- The conversation-completed event must be emitted before external notifications are attempted.
- Each registered notification channel must receive the current agent identity, conversation identity, and one
  `AGENT` message containing the final response.
- An absent final response must be represented as an empty string.
- Notification channels must be invoked in the registration order.
- With no notification channels, completion must still succeed.

## Failure behaviour

- A completion-event delivery failure terminates the stage.
- A notification failure terminates the stage and prevents later notification channels from being invoked.
- Failures are not isolated or retried by completion.

## Acceptance criteria

- A successful stage emits conversation completion once and then notifies every channel once in registration order.
- Each notification contains the exact final response and the correct identities and role.
- Missing response text produces an empty notification message.
- No notification channels is a valid successful case.
