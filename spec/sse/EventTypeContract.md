# SSE event contract

## Purpose

Define the events and payloads exposed by the chat-server sent event stream.

## Wire contract

| Source condition                           | Event name               | Payload                                 | Encoding |
|--------------------------------------------|--------------------------|-----------------------------------------|----------|
| A new conversation is created              | `CONVERSATION_CREATED`   | conversation representation             | JSON     |
| A user-facing stage starts                 | `STAGE`                  | stage ID, `STARTED`, label, no reason   | JSON     |
| A user-facing stage completes              | `STAGE`                  | stage ID, `COMPLETED`, label, no reason | JSON     |
| A user-facing stage reports failure        | `STAGE`                  | stage ID, `FAILED`, label, reason       | JSON     |
| Classification chooses a route             | `DECISION`               | decision mode name and reason           | JSON     |
| Final-response text is emitted             | `TOKEN`                  | one raw text fragment                   | text     |
| The final response has been fully emitted  | `RESPONSE_COMPLETED`     | empty object                            | JSON     |
| Conversation memory is reported as updated | `MEMORY_UPDATED`         | empty object                            | JSON     |
| The workflow reaches successful completion | `CONVERSATION_COMPLETED` | empty object                            | JSON     |
| Request processing fails                   | `ERROR`                  | failure message                         | JSON     |

## Requirements

- Only stages designated as user-facing may produce `STAGE` frames. The internal stages `GUARDRAIL_INPUT`,
  `PERSIST_USER_MESSAGE`, `GUARDRAIL_OUTPUT`, and `PERSIST_RESPONSE` must not appear as stage progress on the wire.
- `COMPLETE` is represented by `CONVERSATION_COMPLETED`, not by a `STAGE` frame.
- Events for one run must preserve production order. In particular, token frames must preserve text-fragment order.
- A serialization failure for an individual JSON event drops that event but must not prevent later events from being
  attempted.
- A transport write failure means the client can no longer be reached and must abort the remaining stream for that turn.
- `MEMORY_UPDATED` is reserved in the event vocabulary. This contract does not define a required emission condition for
  it.

## Acceptance criteria

- Starting, completing, or failing a user-facing stage produces one `STAGE` frame with the corresponding status and
  fields.
- The same event for a non-user-facing stage produces no SSE frame.
- A decision frame contains the chosen mode name and reason.
- Each emitted token becomes one text frame in the same order.
- Successful completion produces `RESPONSE_COMPLETED` after all response tokens and
  `CONVERSATION_COMPLETED` when the workflow finishes.
- A request failure produces an `ERROR` event when the connection remains writable.
