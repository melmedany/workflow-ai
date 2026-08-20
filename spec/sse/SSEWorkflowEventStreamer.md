# SSEWorkflowEventStreamer

## Purpose

Implement `WorkflowEventStreamer` by turning each stage/decision/token/completion callback into a `WorkflowEvent`
and delivering it to the specific consumer registered for that workflow run.

## Inputs / constraints / preconditions

- A consumer must be registered for a `runId` via `registerConsumer` before any of the emit methods (`stageStarted`,
  `stageCompleted`, `stageFailed`, `decisionMade`, `token`, `responseCompleted`,
  `conversationCompleted`) are called for that `runId`.
- Consumers are keyed by `runId`, deliberately NOT by `conversationId`. Two concurrent runs for the same conversation
  (double-submit, retry) must not share or clobber each other's consumer.

## Outputs / constraints / postconditions / invariants

- `stageStarted`/`stageCompleted` build a `WorkflowEvent.StageStarted`/`StageCompleted` with a label from
  `DefaultStageLabelProvider` (`started`/`completed`, both currently returning the same label text. Only `failed`
  adds a `" failed"` suffix) and deliver it to the run's consumer.
- `stageFailed` builds a `WorkflowEvent.StageFailed` carrying the stage id, the `" failed"`-suffixed label, and the
  given reason string verbatim.
- `decisionMade` builds a `WorkflowEvent.DecisionMade` from `decision.decisionMode()` and `decision.reason()`. Only
  those two fields are forwarded. Nothing else on the `RoutingDecision` reaches the event.
- `token` splits `finalResponse` into fragments using `split("(?<=\\s)")` (a lookbehind split that keeps whitespace
  attached to the PRECEDING word) and emits one `WorkflowEvent.Token` per fragment, in order. An input with no
  whitespace produces exactly one token containing the whole string. An empty input (`""`) also produces exactly one
  token, whose text is the empty string (`"".split(...)` yields a single-element array `[""]`, not zero elements).
- `responseCompleted` and `conversationCompleted` build their respective no-payload/text-payload events and deliver them
  to the run's consumer.
- `registerConsumer` overwrites any existing consumer previously registered for the same `runId`.
- `revokeConsumer` removes the mapping for a `runId`. Delivering any event for that `runId` afterward throws.
- Calling ANY emit method for a `runId` with no registered consumer (never registered, or already revoked) throws
  `IllegalStateException` with a message explaining the likely cause.

## Interfaces

- `void registerConsumer(UUID runId, Consumer<WorkflowEvent> eventConsumer)`
- `void revokeConsumer(UUID runId)`
- The `WorkflowEventStreamer` emit methods (`stageStarted`, `stageCompleted`, `stageFailed`, `decisionMade`,
  `token`, `responseCompleted`, `conversationCompleted`).

## Acceptance criteria

- Each emit method delivers exactly one event of the expected `WorkflowEvent` subtype, with the expected field values,
  to the consumer registered for that `runId`.
- Two different `runId`s with two different registered consumers never receive each other's events.
- `token("hello world")` emits two tokens: `"hello "` and `"world"` (whitespace stays attached to the preceding word).
- `token("")` emits exactly one token whose text is the empty string.
- Emitting any event for an unregistered/revoked `runId` throws `IllegalStateException` and does not deliver anything.
- Re-registering a consumer for a `runId` that already had one replaces it. Later events go only to the new consumer.

## Failure modes

- No consumer registered (or already revoked) for the `runId` -> `IllegalStateException`, no event delivered, no event
  silently dropped either. This is a loud failure, not a no-op.

## Edge Cases

- `finalResponse` consisting only of whitespace `split("(?<=\\s)")` still splits it into one-character/whitespace
  fragments (each split point is right after a whitespace character), so e.g. `"  "` (two spaces) produces two
  single-space tokens.

## Non-goals

- Does not decide which `StageId`s are user-facing or otherwise filter/transform events for the wire. That
  filtering/mapping happens entirely in `AgentController.handleEvent` (see the EventType contract spec).
- Does not persist events anywhere. Delivery is purely in-memory and synchronous to the registered consumer.
