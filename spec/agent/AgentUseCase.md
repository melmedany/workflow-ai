# Agent run lifecycle (AgentUseCase)

## Purpose

Run enabled agents while keeping run status and per-run event delivery consistent with success and failure.

## Requirements

- A trigger request may start only when its agent exists and is enabled.
- Each accepted trigger must create one run identity and associate the caller's event consumer with that run before
  workflow execution begins.
- Concurrent runs, including runs for the same conversation, must retain independent run identities and event consumers.
- A successful workflow execution must mark the run complete exactly once.
- A failed workflow execution must mark the run failed exactly once with the failure message and must propagate the
  original failure to the caller.
- The event consumer association must be revoked after execution finishes, whether execution succeeds or fails.
- Reloading, removing, listing, or inspecting agents must not create or alter run records.
- A failed run is terminal. This service does not retry it.

## Failure behaviour

- If the agent is unknown or disabled, the request must fail before a run is created or an event consumer is registered.
- If workflow execution fails, the run must not also be marked complete.
- Clean-up of the event consumer is required even when run completion or failure reporting itself fails.

## Acceptance criteria

- A successful trigger creates one run, delivers events only to that run's consumer, marks the run complete, and removes
  the consumer association.
- A workflow failure creates one run, marks it failed with the original failure message, propagates that failure, and
  removes the consumer association.
- Triggering an unknown or disabled agent creates no run and performs no run clean-up.
