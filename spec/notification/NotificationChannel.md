# NotificationChannel (port contract)

## Purpose

Define the contract any external notification mechanism (email, WhatsApp, Slack, push, etc.) must satisfy to deliver a
finished agent response to a user outside the chat/SSE connection. No implementation exists yet, this spec states what
an implementer must guarantee, not any current behaviour.

## Inputs / constraints / preconditions

- `notify(UUID agentId, UUID conversationId, ConversationMessage message)` is called once per completed workflow turn,
  from `CompleteStage`, for every registered `NotificationChannel` bean.
- `agentId` and `conversationId` identify which agent/conversation the message belongs to. `message` is always an
  `AGENT`-role `ConversationMessage` whose `content()` is the final response text for that turn (or `""` if no response
  was generated for some reason, never `null`).
- Implementations must expect to be called from `CompleteStage`'s calling thread/context and should not assume any
  particular threading model beyond, called once per completed turn, synchronously with the rest of `COMPLETE`.

## Outputs / constraints / postconditions / invariants

- `notify` returns `void`, the port defines no way to report partial success or a delivery receipt back to the caller.
- An implementer MUST NOT block indefinitely: `CompleteStage` calls every registered channel in sequence, so a slow or
  hanging channel delays (or, if it throws, aborts) `COMPLETE`'s remaining channels, see Failure modes.
- An implementer SHOULD be idempotent-safe or otherwise tolerate being invoked at most once per successful turn. The
  port itself provides no deduplication.
- An implementer MUST NOT mutate the given `ConversationMessage` (it's an immutable record, so this is naturally
  enforced) and MUST NOT assume it can safely retain and reuse the same instance for unrelated purposes beyond reading
  it.

## Interfaces

- `void notify(UUID agentId, UUID conversationId, ConversationMessage message)`

## Acceptance criteria (for any future implementation)

- Given a valid `agentId`, `conversationId`, and non-null `message`, the implementation delivers (or queues for
  delivery) the message content to whatever external destination it's responsible for, without altering the text.
- The implementation resolves which concrete destination (email address, phone number, channel id, etc.) to use for a
  given `agentId`/`conversationId` internally, the port provides no such mapping itself.
- An implementation that cannot resolve a destination for a given agent/conversation (e.g. user has no email on file)
  should fail gracefully (e.g. log and return) rather than propagate an exception that would abort sibling channels'
  notifications for the same turn. This is a good practice, not enforced by the port's own type signature.

## Failure modes

- Per `CompleteStage`'s current contract (see its own spec), if `notify` throws, the exception propagates uncaught out
  of `CompleteStage.execute`, and, because channels are notified in registration order in a simple loop with no
  per-channel isolation, any channels registered after the failing one are never called for that turn. Implementers
  should treat exceptions as something the caller does NOT protect them from, and should therefore catch and handle/log
  their own internal failures rather than let them propagate, unless a hard failure is genuinely desired.

## Edge Cases

- Multiple `NotificationChannel` beans registered for the same delivery mechanism (e.g. two email senders), the port
  doesn't prevent duplicate registrations. That's a wiring/configuration concern outside this contract.
- `message.content()` being an empty string, must still be treated as a valid (if uninformative) notification, not an
  error condition.

## Non-goals

- Does not define retry, batching, rate-limiting, or delivery-confirmation semantics. Those are entirely up to each
  implementation.
- Does not define how a channel discovers per-agent/per-conversation destination configuration (email address, phone
  number, webhook URL, etc.). That's implementation-specific.
- Does not cover channel enable/disable or per-agent opt-in/opt-out logic.
