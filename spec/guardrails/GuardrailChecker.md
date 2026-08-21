# Input/OutputBlocklist matching (GuardrailChecker)

## Purpose

Define the matching rules shared by input and output blocklists.

## Requirements

- Input and output use separate ordered lists. A term configured for one direction must not affect the other.
- Matching is case-insensitive and literal. Characters with special meaning in pattern languages have no special meaning
  in a configured term.
- A term matches only on word boundaries. It must not match as a substring of a longer word.
- Runs of whitespace in checked text are treated as one space, and leading or trailing whitespace is ignored.
- A multi-word term matches when its words occur contiguously under those whitespace-normalization rules.
- When several configured terms match, the earliest term in the configuration order is reported, preserving its
  configured spelling and spacing.
- Null or empty text and an empty blocklist produce no match.
- Matching must not modify the text or the configured lists and must have no externally visible side effects.

## Acceptance criteria

- `art` matches `Art matters` but not `start here`.
- A configured phrase matches despite differences in the letter case and runs of spaces, tabs, or newlines in the
  checked text.
- A configured term containing pattern metacharacters is treated as literal text.
- When two configured terms match, the first configured term is returned.
- Null or empty text produces no match.

## Non-goals

- Fuzzy, typo-tolerant, and semantic matching are outside this contract.
- This contract does not decide the consequence of a match.
