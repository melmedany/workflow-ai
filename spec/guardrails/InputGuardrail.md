# Input blocklist enforcement (InputGuardrail)

## Purpose

Prevent a user message containing a configured input-blocked term from being submitted to a model.

## Requirements

- Every outbound user message must be checked against the input blocklist before model processing.
- A message with no matching term must pass unchanged.
- A message with a matching term must fail input validation and identify the matched term exactly as configured.
- A block is a validation outcome, not a redaction or rewrite of the message.
- Input matching must follow the common blocklist rules in [GuardrailChecker.md](GuardrailChecker.md).

## Acceptance criteria

- Text containing no configured input-blocked term passes validation.
- Text containing a configured input-blocked term fails validation and reports that term.
- Case, whitespace, phrase, and word-boundary behaviour is identical to the common blocklist contract.
