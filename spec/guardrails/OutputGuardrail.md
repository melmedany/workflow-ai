# Output blocklist enforcement (OutputGuardrail)

## Purpose

Prevent a model response containing a configured output-blocked term from being returned as an accepted response.

## Requirements

- Every generated response subject to output guardrails must be checked before it is returned to its caller.
- A response with no matching term must pass unchanged.
- A response with a matching term must fail output validation and identify the matched term exactly as configured.
- A block is a validation outcome, not a redaction or rewrite of the response.
- Output matching must follow the common blocklist rules in [GuardrailChecker.md](GuardrailChecker.md).

## Acceptance criteria

- Text containing no configured output-blocked term passes validation.
- Text containing a configured output-blocked term fails validation and reports that term.
- Case, whitespace, phrase, and word-boundary behaviour is identical to the common blocklist contract.
