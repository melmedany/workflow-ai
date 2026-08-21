# Memory compaction (CompactMemoryStage)

## Purpose

Update a conversation's compact memory with durable information from the latest exchange after a visible response has
been produced.

## Requirements

- When memory is disabled, compaction must perform no model request, storage access, or memory progress emission.
- When memory is enabled, a compaction-started event must be emitted before any compaction work and a
  compaction-completed event must be emitted when the stage finishes.
- A missing, empty, or whitespace-only final response must skip model generation and leave stored memory unchanged.
- For a non-blank response, compaction must use the prior memory, user message, final response, and agent context to
  produce replacement memory.
- A non-blank compaction result must replace the stored memory for the current agent and conversation.
- A null, empty, or whitespace-only compaction result must leave existing memory unchanged.
- Memory compaction must not alter the workflow response or routing state.

## Failure behaviour

- A compaction-generation failure must leave stored memory unchanged and must not prevent the workflow from completing.
- For memory-enabled runs, the completed event is still required after a handled compaction failure.

## Acceptance criteria

- A memory-disabled run performs no compaction work and emits no memory stage events.
- A memory-enabled run with no usable response leaves memory untouched and still completes the memory stage.
- A successful non-blank result replaces memory for the correct agent and conversation.
- A blank result or generation failure leaves existing memory untouched and does not fail the turn.
