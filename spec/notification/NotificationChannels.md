# Completion notification contract (NotificationChannels)

## Purpose

Define how a completed agent response is handed to external notification channels such as email, messaging, or push
delivery.

## Requirements

- On successful workflow completion, every registered channel must receive the agent identity, conversation identity,
  and final response as an `AGENT` message.
- The notification text must equal the final response without alteration. When no response was generated, the content
  must be an empty string rather than null.
- A channel may deliver immediately or queue delivery, but must not claim success without accepting responsibility for
  the message.
- Destination lookup, retry, batching, rate limiting, and delivery confirmation are channel-specific concerns and are
  not defined by this contract.
- Notification delivery must not mutate the supplied message.

## Failure behaviour

- Channels are invoked in registration order as part of workflow completion.
- A channel failure terminates completion and prevents channels later in that order from being invoked.

## Acceptance criteria

- Each registered channel receives one notification for a successfully completed turn, with the correct agent,
  conversation, role, and exact response text.
- An empty response is delivered as a valid empty message.
- With no registered channels, workflow completion succeeds without notification work.
- When a channel fails, the failure is visible to the workflow and no later channel is notified.
