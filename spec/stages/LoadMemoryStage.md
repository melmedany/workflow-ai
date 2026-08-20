# LoadMemoryStage

## Purpose

Load the conversation's compact memory blob into `WorkflowState` when the agent has memory enabled, so later stages can
feed it to the model.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `conversationId()`, `agentProperties().id()`, and
  `agentProperties().memoryEnabled()`.

## Outputs / constraints / postconditions / invariants

- Always returns a map containing `WorkflowState.KEY_MEMORY_CONTEXT` bound to a non-null `String`.
- When `agentProperties().memoryEnabled()` is `false`, `KEY_MEMORY_CONTEXT` is `""` and
  `AgentMemoryStorage.getMemory` is never called.
- When `memoryEnabled()` is `true`, `KEY_MEMORY_CONTEXT` is the stored memory content for
  `(conversationId, agentId)`, or `""` if no memory exists yet for that pair.
- Emits `stageStarted(runId, LOAD_MEMORY)` before the lookup and `stageCompleted(runId, LOAD_MEMORY)` after it, on every
  registered `WorkflowEventStreamer`.

## Interfaces

- `StageId stageId()` -> `StageId.LOAD_MEMORY`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- Memory-disabled agent: no call to `AgentMemoryStorage`, result memory context is empty string.
- Memory-enabled agent with existing memory: result memory context equals the stored value exactly.
- Memory-enabled agent with no stored memory yet: result memory context is empty string, no exception.
- `stageStarted`/`stageCompleted` fire exactly once each, only when memory is enabled.

## Failure modes

- If `AgentMemoryStorage.getMemory` throws, the exception propagates uncaught. `stageCompleted` is not emitted in that
  case.

## Edge Cases

- Stored memory content is an empty string (as opposed to absent), treated the same as absent, since it is passed
  through as-is either way.

## Non-goals

- Does not write, compact, or rewrite memory, that is `COMPACT_MEMORY`'s responsibility.
- Does not enforce any size/format constraints on the loaded memory blob.
