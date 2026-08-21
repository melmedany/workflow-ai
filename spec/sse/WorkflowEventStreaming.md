# Per-run workflow event delivery (WorkflowEventStreaming)

## Purpose

Deliver workflow progress, decisions, response text, and completion signals to the consumer associated with one run.

## Requirements

- A consumer must be registered before any event is emitted for its run.
- Consumers are associated with run identity, not conversation identity. Concurrent runs for one conversation must not
  receive one another's events.
- Registering another consumer for the same run replaces the previous consumer for later events.
- Revoking a run removes its consumer association. Emitting after revocation or before registration must fail rather
  than silently discard the event.
- A stage-started or stage-completed event contains the stage identity and its configured label. A stage-failed event
  additionally contains the failure reason and uses the failed-stage label.
- A decision event contains only the decision mode and reason.
- Final-response text is split after each whitespace character, keeping whitespace attached to the preceding fragment
  and emitted in order. Text with no whitespace is one fragment. Empty text is one empty fragment.
- Response-completed and conversation-completed signals are each delivered as a single event.
- Delivery is immediate. This contract does not require event persistence.

## Failure behaviour

- Emitting any event without a consumer for that run must fail and deliver nothing.
- A consumer failure propagates to the emitter. Events are not retried by this component.

## Acceptance criteria

- Each emission reaches only the run's registered consumer and preserves all contract fields.
- Two simultaneous runs for one conversation remain isolated.
- `hello world` is emitted as `hello ` followed by `world`.
- Two spaces are emitted as two single-space fragments, and an empty response produces one empty fragment.
- Re-registering a run directs later events only to the replacement consumer.
- After revocation, further emission for that run fails.
