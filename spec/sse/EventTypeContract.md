# SSE EventType contract

## Purpose

Define the wire contract for the chat SSE stream: which `EventType` each internal `WorkflowEvent` becomes, what payload
(if any) it carries, and which events are actually reachable given today's producers.

## Inputs / constraints / preconditions

- Produced by `AgentController.handleEvent`, which receives each `WorkflowEvent` from the callback passed to
  `AgentUseCase.trigger` and maps it to zero or one SSE frame via `sendJson`/`sendText`.
- Every `EventType` value: `CONVERSATION_CREATED`, `DECISION`, `TOKEN`, `RESPONSE_COMPLETED`, `MEMORY_UPDATED`,
  `CONVERSATION_COMPLETED`, `ERROR`, `STAGE`.

## Outputs / constraints / postconditions / invariants

Full mapping table:

| `WorkflowEvent`                                | `EventType`              | Payload                                         | Format                                                                                                                                                                                     |
|------------------------------------------------|--------------------------|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| (new conversation only, not a `WorkflowEvent`) | `CONVERSATION_CREATED`   | `ConversationResponse`                          | JSON                                                                                                                                                                                       |
| `StageStarted`                                 | `STAGE`                  | `StagePayload(stageId, STARTED, label, null)`   | JSON: only sent when `stageId.isUserFacing()`                                                                                                                                              |
| `StageCompleted`                               | `STAGE`                  | `StagePayload(stageId, COMPLETED, label, null)` | JSON: only sent when `stageId.isUserFacing()`                                                                                                                                              |
| `StageFailed`                                  | `STAGE`                  | `StagePayload(stageId, FAILED, label, reason)`  | JSON: only sent when `stageId.isUserFacing()`                                                                                                                                              |
| `DecisionMade`                                 | `DECISION`               | `DecisionPayload(mode.name(), reason)`          | JSON                                                                                                                                                                                       |
| `Token`                                        | `TOKEN`                  | the raw token text                              | plain text (not JSON-wrapped)                                                                                                                                                              |
| `ResponseCompleted`                            | `RESPONSE_COMPLETED`     | none (`"{}"`)                                   | JSON                                                                                                                                                                                       |
| `MemoryUpdated`                                | `MEMORY_UPDATED`         | none (`"{}"`)                                   | JSON: **currently unreachable**: nothing implements/calls a method that emits this event (`WorkflowEventStreamer` has no `memoryUpdated(...)` method)                                      |
| `ConversationCompleted`                        | `CONVERSATION_COMPLETED` | none (`"{}"`)                                   | JSON                                                                                                                                                                                       |
| `Error`                                        | `ERROR`                  | `ErrorPayload(message)`                         | JSON: **only reachable today via the controller's own catch block (`sendError`)**, never via the `WorkflowEvent.Error` switch case, since nothing in the port constructs that event either |

- Non-user-facing stages (`GUARDRAIL_INPUT`, `PERSIST_USER_MESSAGE`, `GUARDRAIL_OUTPUT`, `PERSIST_RESPONSE`) never
  produce a `STAGE` SSE frame for their start/complete/fail events, even though the underlying stage still runs and
  still emits the `WorkflowEvent` internally. The filtering happens at the SSE boundary, not at the stage.
- `COMPLETE` is marked `isUserFacing() == true`, but no `StageStarted`/`StageCompleted` event is ever constructed for it
  (`CompleteStage` emits `ConversationCompleted` instead). The flag is present but has no observable effect for this
  particular stage.
- `sendJson` catches `IOException` (client-disconnected / broken pipe) by logging and RE-THROWING as a
  `RuntimeException`, aborting the rest of the event stream for that turn. It catches any OTHER exception (e.g. a JSON
  serialization failure) by only logging. That single event is silently dropped and processing continues. This
  asymmetry is deliberate: an unreachable client should stop everything. A payload that fails to serialize should not.
- `sendText` (used only for `TOKEN`) only guards against `IOException`, with the same abort-on-IOException behaviour.
  There is no secondarily catch-all.

## Interfaces

- `EventType` enum (SSE frame name, sent via `SseEmitter.event().name(eventType.name())`).
- `AgentController.handleEvent(SseEmitter, WorkflowEvent)` (private, the switch that implements this table).

## Acceptance criteria

- A `StageStarted`/`StageCompleted`/`StageFailed` event for a user-facing stage results in exactly one `STAGE` SSE frame
  with the corresponding status.
- The same events for a non-user-facing stage result in NO SSE frame at all.
- A `DecisionMade` event's payload always contains the decision's mode name and reason, never nulls for those two fields
  (assuming the underlying `RoutingDecision` fields are populated).
- Every token from `WorkflowEventStreamer.token(...)` results in one `TOKEN` text frame, in the same relative order they
  were emitted.

## Failure modes

- A downstream SSE write failure (`IOException`) aborts the remainder of that turn's event stream.
- A payload serialization failure for a single event is swallowed. The stream continues with later events.

## Non-goals

- Does not define retry/reconnect semantics for the SSE connection itself (left to the HTTP/SSE transport and client).
