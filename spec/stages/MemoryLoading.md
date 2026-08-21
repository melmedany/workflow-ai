# Memory loading (LoadMemoryStage)

## Purpose

Make the conversation's compact memory available to later workflow stages when memory is enabled for the agent.

## Requirements

- The stage must always provide a non-null memory context.
- When memory is disabled, the context must be an empty string and no memory storage access or memory progress event
  may occur.
- When memory is enabled, a memory-load started event must be emitted before retrieval.
- Existing memory for the current agent and conversation must be returned exactly as stored.
- If no memory exists, the context must be an empty string.
- After successful retrieval, a memory-load completed event must be emitted.
- Loading memory must not modify stored memory.

## Failure behaviour

- A storage failure terminates the stage and propagates to the workflow.
- If retrieval fails, the completed event must not be emitted.

## Acceptance criteria

- A memory-disabled agent receives an empty context with no storage access or memory stage events.
- A memory-enabled agent receives the exact stored memory or an empty string when none exists.
- A successful enabled lookup emits one started event followed by one completed event.
- A failed enabled lookup emits the started event, propagates the failure, and does not emit completion.
