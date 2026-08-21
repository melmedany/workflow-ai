# STANDARD workflow

## Purpose

Define the supported workflow mode and the stages visited for each trigger source and routing outcome.

## Supported mode

- `STANDARD` is the only supported workflow mode.
- Requesting any other workflow mode must fail workflow construction.
- Workflow construction must fail if any stage required by `STANDARD` is unavailable and must not return a partially
  usable workflow.

## Routing requirements

Every run begins with:

`PERSIST_USER_MESSAGE -> LOAD_MEMORY`

After `LOAD_MEMORY`:

- `USER_MESSAGE` continues to `CLASSIFICATION`.
- `SYSTEM_TRIGGER` skips classification and continues directly to `EXECUTE_WORKFLOW`.

After `CLASSIFICATION`:

- `EXECUTE` without a scheduling request continues to `EXECUTE_WORKFLOW`.
- `EXECUTE` with a scheduling request continues to `CREATE_TASK`.
- `CLARIFY`, `GREET`, `REDIRECT`, and `REFUSE` continue to their corresponding `GENERATE_*` stage.
- An absent decision is treated as `REFUSE`.

After `CREATE_TASK`:

- `EXECUTE` continues to `COMPACT_MEMORY`.
- `CLARIFY` continues to `GENERATE_CLARIFICATION`.
- `REFUSE` continues to `GENERATE_REFUSAL`.
- No other decision mode is valid at this point.

The remaining paths are fixed:

- `EXECUTE_WORKFLOW -> SELF_VERIFICATION -> COMPACT_MEMORY`
- every `GENERATE_*` stage `-> COMPACT_MEMORY`
- `COMPACT_MEMORY -> COMPLETE -> END`

Every successful path must reach `COMPLETE` exactly once and then terminate.

## Failure behaviour

- A stage failure must terminate the run as failed. Execution must not continue along another branch.
- The reported failure must identify the failed stage and preserve the original failure as its cause.
- An invalid or unmapped routing result must fail the run rather than selecting an arbitrary branch.

## Acceptance criteria

- A user message classified as ordinary `EXECUTE` visits `PERSIST_USER_MESSAGE`, `LOAD_MEMORY`, `CLASSIFICATION`,
  `EXECUTE_WORKFLOW`, `SELF_VERIFICATION`, `COMPACT_MEMORY`, and `COMPLETE`, in that order.
- A scheduling `EXECUTE` visits `CREATE_TASK` instead of `EXECUTE_WORKFLOW`. Its subsequent route follows the
  `CREATE_TASK` result.
- `CLARIFY`, `GREET`, `REDIRECT`, and `REFUSE` each visit only their matching response stage before memory compaction
  and completion.
- A system-triggered run visits `EXECUTE_WORKFLOW` without visiting `CLASSIFICATION` or `CREATE_TASK`.
- Missing required stages, unsupported workflow modes, stage failures, and invalid routing results fail explicitly.
