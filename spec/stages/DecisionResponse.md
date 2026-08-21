# Decision response generation (DecisionResponseGenerator)

## Purpose

Generate short responses for `GREET`, `REDIRECT`, and `REFUSE` decisions while providing a stable fallback when text
generation is unavailable.

## Requirements

- Generation must use the prompt supplied by the selected decision branch together with the agent's system context and
  conversation memory.
- A successful generation returns the accepted provider text without modification.
- Any failure while selecting a provider, preparing the request, applying guardrails, or generating text must return the
  agent policy's `failedToProcessMessage` value.
- Generation failures in these branches must not fail the workflow.
- No retry is performed.
- This behaviour only selects the response text. Persistence and client emission are separate final-response
  requirements.

## Acceptance criteria

- Successful generation returns the generated text exactly.
- Every generation failure category returns the configured fallback and does not propagate the underlying failure.
- A blank or null configured fallback is returned as configured. This component does not replace it with another value.
