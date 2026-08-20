# CompactMemoryStage

## Purpose

Rewrite the conversation's compact memory blob after a visible turn completes, folding in durable facts/preferences from
the latest exchange, when the agent has memory enabled.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `conversationId()`, `memoryContext()`, `userMessage()`,
  `generatedResponse()`, and `agentProperties()` (`id()`, `systemPrompt()`, `memoryEnabled()`).

## Outputs / constraints / postconditions / invariants

- When `agentProperties().memoryEnabled()` is `false`, the stage does nothing further and returns an empty map, no
  provider call, no `AgentMemoryStorage` interaction.
- When memory is enabled and `generatedResponse()` is blank/absent, compaction is skipped silently (nothing to fold in
  yet), no provider call, no storage write.
- When memory is enabled and there is a non-blank generated response, the stage calls the configured provider
  (non-streamed `call(...)`) with a compaction prompt built from the previous memory, the user message, and the
  response, and if the result is non-blank, replaces the stored memory for `(conversationId, agentId)` via
  `AgentMemoryStorage.replace`.
- A blank/`null` compaction result from the provider is treated as "nothing to update": the existing stored memory is
  left untouched.
- Always returns an empty map, this stage never contributes new `WorkflowState` keys.
- Emits `stageStarted(runId, COMPACT_MEMORY)` before doing any work and `stageCompleted(runId, COMPACT_MEMORY)`
  exactly once per execution, but only when `agentProperties().memoryEnabled()` is `true` (mirrors `LOAD_MEMORY`'s
  event-emission rule). This holds on every enabled-memory path, whether compaction runs, is skipped (blank
  response), or fails.

## Interfaces

- `StageId stageId()` -> `StageId.COMPACT_MEMORY`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- Memory disabled: zero interactions with the chat provider or `AgentMemoryStorage`, and neither `stageStarted` nor
  `stageCompleted` fires.
- Memory enabled, blank/no generated response: zero interactions with the chat provider or `AgentMemoryStorage`.
- Memory enabled, non-blank response, successful compaction: `AgentMemoryStorage.replace` is called with the compacted
  text for the correct `(conversationId, agentId)`.
- Memory enabled, provider call throws: the exception is caught and logged. The stage does not propagate it, and
  `AgentMemoryStorage.replace` is never called.
- Memory enabled, provider returns a blank compaction result: `AgentMemoryStorage.replace` is never called.
- When memory is enabled, `stageStarted`/`stageCompleted` each fire exactly once per execution, on every registered
  `WorkflowEventStreamer`, regardless of which enabled-memory path was taken.

## Failure modes

- Any exception while building the compaction request or calling the provider is caught and logged. It never propagates
  and never blocks the turn from completing.

## Edge Cases

- `generatedResponse()` present but exactly blank (`""` or whitespace) is treated the same as absent.

## Non-goals

- Does not load memory, that is `LOAD_MEMORY`'s responsibility.
- Does not decide what counts as "durable" information. That judgment is delegated entirely to the compaction
  prompt/model.
