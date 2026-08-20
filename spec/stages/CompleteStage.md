# CompleteStage

## Purpose

Mark the workflow run as finished for SSE clients and fan out the final agent response to any registered notification
channels. This is the terminal node every graph path converges on.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `generatedResponse()`, `agentProperties().id()`, and `conversationId()`.
- Every path through the STANDARD workflow graph ends here (see the graph invariant that all paths reach
  `COMPLETE`).

## Outputs / constraints / postconditions / invariants

- Emits `conversationCompleted(runId)`. NOT `stageStarted`/`stageCompleted` for `StageId.COMPLETE`, on every registered
  `WorkflowEventStreamer`. This differs deliberately from every other stage: `COMPLETE` is the terminal signal for the
  whole run, not an intermediate stage progress event.
- Builds one `ConversationMessage` (role `AGENT`, content = `generatedResponse()` or `""` if absent) and calls
  `NotificationChannel.notify(agentId, conversationId, message)` once for every registered `NotificationChannel`.
- Always returns an empty map, this is the last node. Nothing downstream consumes its output.

## Interfaces

- `StageId stageId()` -> `StageId.COMPLETE`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- `conversationCompleted(runId)` is emitted exactly once per execution, for every registered streamer.
- Every registered `NotificationChannel` receives exactly one `notify` call with the correct `agentId`,
  `conversationId`, and an `AGENT`-role message containing the final response text.
- With zero registered notification channels, the stage still completes normally (emits `conversationCompleted`, returns
  an empty map) without error.
- With an absent `generatedResponse()`, the notified message content is `""`, not `null` and not an exception.

## Failure modes

- If a `NotificationChannel.notify` call throws, the exception propagates uncaught (no per-channel error isolation at
  this stage). A single failing channel can prevent later channels in the list from being notified. This applies equally
  to `conversationCompleted` throwing (no isolation from streamer to streamer either).

## Edge Cases

- Multiple notification channels are registered. Each receives its own, independently constructed but identical
  `ConversationMessage` instance is not guaranteed. Only content equality is guaranteed.

## Non-goals

- Does not persist the response, persistence already happened earlier (`PersistResponseStage` /
  `CreateTaskStage`).
- Does not decide which channels are active for a given agent/conversation, that filtering, if any, is the channel
  implementation's responsibility.
