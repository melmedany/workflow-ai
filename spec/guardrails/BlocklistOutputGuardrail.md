# BlocklistOutputGuardrail

## Purpose

Adapt `GuardrailChecker`'s output check to LangChain4j's `OutputGuardrail` contract, so a chat provider can reject a
generated model response that contains a blocked term before it is returned to the caller.

## Inputs / constraints / preconditions

- Receives the `AiMessage` produced by the model.
- Depends on a `GuardrailChecker` (injected), which holds the configured `output-blocked-terms`.

## Outputs / constraints / postconditions / invariants

- Returns `OutputGuardrailResult.success()` when `GuardrailChecker.checkOutput` finds no blocked term in
  `chatResponse.text()`.
- Returns a non-fatal `failure("Blocked term: " + term)` when a blocked term is found, naming the exact matched term.
- The check is delegated entirely to `GuardrailChecker.checkOutput`. This class adds no additional matching logic of
  its own.

## Interfaces

- `OutputGuardrailResult validate(AiMessage chatResponse)`

## Acceptance criteria

- A response containing no configured blocked term validates successfully.
- A response containing a configured blocked term fails validation, and the failure message includes that term.

## Failure modes

- Blocked-term match → non-fatal `failure(...)`, allowing the caller (`AbstractChatProvider`) to decide the
  consequence (currently: replace the response text with `WorkflowPrompts.GUARDRAIL_FALLBACK_MESSAGE`).

## Edge Cases

- Response with a blocked term differing only in case or surrounded by extra whitespace still fails (inherited from
  `GuardrailChecker`).

## Non-goals

- Does not modify, redact, or rewrite the model's response text.
- Does not itself decide the user-facing consequence of a block (e.g. fallback messaging). That is the caller's
  responsibility.
