# GuardrailChecker

## Purpose

Match free text against a configured list of blocked terms, separately for input and output, and report the first
term that matched.

## Inputs / constraints / preconditions

- Constructed once with two term lists: `inputBlockedTerms` and `outputBlockedTerms` (from
  `workflow-ai.guardrail.input-blocked-terms` / `output-blocked-terms` in `application.yml`). Lists may be empty but
  not `null`.
- `checkInput(String text)` / `checkOutput(String text)` accept any `String`, including `null` or blank.
- Terms are checked independently per list. A term configured only for input never blocks output and vice versa.

## Outputs / constraints / postconditions / invariants

- Returns `Optional<String>` holding the matched term exactly as configured (original casing/spacing), or
  `Optional.empty()` when nothing matched.
- Matching is case-insensitive.
- Matching is on whole-word boundaries: a configured term must appear as a standalone word/phrase in the text, not as
  a substring of a longer word (e.g. term `"art"` must not match inside `"start"`).
- Runs of whitespace (including newlines/tabs) in the checked text are collapsed to a single space, and the text is
  trimmed before matching, so formatting differences alone never prevent a match.
- When multiple configured terms match, the first match found (iteration order of the configured list) is returned.
- The checker is stateless/side-effect-free: calling `checkInput`/`checkOutput` never mutates configuration or
  produces observable side effects.

## Interfaces

- `GuardrailChecker(List<String> inputBlockedTerms, List<String> outputBlockedTerms)`
- `Optional<String> checkInput(String text)`
- `Optional<String> checkOutput(String text)`

## Acceptance criteria

- A configured term matches regardless of case in the checked text.
- A configured term matches even when surrounded by extra/irregular whitespace.
- A configured term does not match when it only appears as part of a larger word.
- A multi-word configured term matches only when its words appear contiguously (as a phrase) in the checked text.
- `null` or empty text yields `Optional.empty()`.
- An empty blocked-term list yields `Optional.empty()` for any input.

## Failure modes

- No blocked terms configured -> always returns `Optional.empty()`. This is normal, not an error.
- Terms containing regex metacharacters are treated as literal text (quoted), so they can never cause a pattern
  compilation error or unintended regex behaviour.

## Edge Cases

- Blocked term appearing at the very start or end of the text.
- Blocked term differing only by case or internal whitespace from the configured term.
- Text containing the blocked term as a prefix/suffix of another word (must NOT match).
- Duplicate terms in the configured list (should not cause errors. First occurrence still reported).

## Non-goals

- No fuzzy/typo-tolerant or semantic matching. Only literal, whole-word, case-insensitive matching.
- Does not redact, sanitize, or rewrite the checked text.
- Does not decide what to do with a match (that is the caller's responsibility, e.g. `BlocklistInputGuardrail` /
  `BlocklistOutputGuardrail`).
