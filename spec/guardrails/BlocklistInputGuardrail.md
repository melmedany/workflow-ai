# BlocklistInputGuardrail

## Purpose

Adapt `GuardrailChecker`'s input check to LangChain4j's `InputGuardrail` contract, so a chat provider can reject a
user message before it reaches the model.

## Inputs / constraints / preconditions

- Receives the `UserMessage` LangChain4j is about to send it to the model.
- Depends on a `GuardrailChecker` (injected), which holds the configured `input-blocked-terms`.
- Assumes the `UserMessage` carries a single text segment (`singleText()`).

## Outputs / constraints / postconditions / invariants

- Returns `InputGuardrailResult.success()` when `GuardrailChecker.checkInput` finds no blocked term.
- Returns a non-fatal `failure("Blocked term: " + term)` when a blocked term is found, naming the exact matched term.
- Never throws for a well-formed single-text `UserMessage`. The guardrail's decision is fully expressed in the
  returned result, not via exceptions.

## Interfaces

- `InputGuardrailResult validate(UserMessage userMessage)`

## Acceptance criteria

- A message containing no configured blocked term validates successfully.
- A message containing a configured blocked term fails validation, and the failure message includes that term.
- The check is delegated entirely to `GuardrailChecker.checkInput`. This class adds no additional matching logic of
  its own.

## Failure modes

- Blocked-term match → non-fatal `failure(...)`, allowing the caller (`AbstractChatProvider`) to decide the
  consequence (currently: raise `GuardrailBlockedException`).

## Edge Cases

- Message with a blocked term differing only in case or surrounded by extra whitespace still fails (inherited from
  `GuardrailChecker`).

## Non-goals

- Does not modify, redact, or rewrite the user message.
- Does not itself decide the user-facing consequence of a block (e.g. throwing, fallback messaging). That is the
  caller's responsibility.
