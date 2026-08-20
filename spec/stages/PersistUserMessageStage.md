# PersistUserMessageStage

## Purpose

Store the message that triggered this workflow run on the conversation, tagged with the correct role depending on
who/what triggered the run.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `conversationId()`, `agentProperties().id()`, `triggerSource()`, and
  `userMessage()`.
- `triggerSource()` defaults to `USER_MESSAGE` when not set on the state.

## Outputs / constraints / postconditions / invariants

- Exactly one `ConversationMessage` is saved via `ConversationMessageStorage.save(conversationId, agentId, message)`
  per execution.
- The saved message's role is `SYSTEM` when `triggerSource() == SYSTEM_TRIGGER` (a scheduler-fired run), and `USER`
  for any other trigger source (i.e. a person's chat message).
- The saved message's content is exactly `state.userMessage()`, unmodified.
- Emits `stageStarted(runId, PERSIST_USER_MESSAGE)` before saving and `stageCompleted(runId, PERSIST_USER_MESSAGE)`
  after saving, on every registered `WorkflowEventStreamer`.
- Returns an empty state-update map, this stage contributes no new keys to `WorkflowState`.

## Interfaces

- `StageId stageId()` → `StageId.PERSIST_USER_MESSAGE`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- A user-triggered run persists the message with role `USER`.
- A scheduler-triggered run (`SYSTEM_TRIGGER`) persists the message with role `SYSTEM`.
- The persisted message content matches the state's `userMessage()` verbatim.
- Both streamer events fire exactly once, in order, for every execution, including when storage succeeds.

## Failure modes

- If `ConversationMessageStorage.save` throws, the exception propagates uncaught. `stageCompleted` is not emitted in
  that case, since it happens after the save call.

## Edge Cases

- Empty/blank `userMessage()`, still persisted as-is. No validation performed by this stage.

## Non-goals

- Does not read or validate existing conversation history.
- Does not decide *whether* to persist based on message content (e.g. no guardrail or dedup logic here).
