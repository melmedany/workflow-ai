# AgentDefinitionService

## Purpose

Validate an `AgentDefinition` (chat provider/model support and workflow support) before it is ever persisted or
used to run an agent, and keep the live `AgentUseCase` runtime in sync with storage on save/update/delete.

## Inputs / constraints / preconditions

- `saveDefinition`/`updateDefinition` accept an `AgentDefinition` whose `chatProperties().providerId()`/`model()`
  and `workflowId()` are checked before any persistence happens.
- Validation depends on `ChatProviderRegistry.validate` (provider existence + model support) and
  `WorkflowExecutorFactory.isSupported` (workflow variant support).

## Outputs / constraints / postconditions / invariants

- **Validation collects ALL applicable errors before failing:** an unknown/unsupported chat provider (or
  unsupported model for a known provider) AND an unsupported workflow are both reported together in one
  `AgentValidationException`, joined by `"; "`, rather than failing fast on the first problem.
- A known provider that doesn't support the requested model raises a `ChatProviderException` internally, which is
  caught and folded into the same validation error list (not rethrown as-is).
- An unknown provider raises an `IllegalArgumentException` internally, also caught and folded into the same list.
- If validation passes (no errors), `saveDefinition` persists via `storagePort.save` and then calls
  `agentService.reload(agentId)`. The reload always happens AFTER a successful save, using the persisted
  definition's id.
- `updateDefinition` follows the same pattern via `storagePort.update` and reloads using the *updated* definition's
  id (from the storage response, not necessarily the input object).
- If validation fails, NEITHER `storagePort.save`/`update` NOR `agentService.reload` is ever called. Validation
  fully gates persistence and reload.
- `deleteDefinition` removes the agent from the live runtime (`agentService.remove`) BEFORE deleting it from storage
  (`storagePort.delete`). The ordering is: stop it from running first, then remove its record.
- `getAllDefinitions`, `getDefinition`, `workflowDiagram`, and `supportedChatProviders` are pure passthroughs to
  `storagePort`/`agentService`/`chatProviderRegistry` respectively, with no validation or side effects.

## Interfaces

- `Map<ChatProviderId, Set<String>> supportedChatProviders()`
- `List<AgentDefinition> getAllDefinitions()`
- `AgentDefinition getDefinition(UUID agentId)`
- `AgentDefinition saveDefinition(AgentDefinition definition)`
- `AgentDefinition updateDefinition(AgentDefinition definition)`
- `String workflowDiagram(UUID agentId)`
- `void deleteDefinition(UUID agentId)`

## Acceptance criteria

- A definition with a valid, known provider+model and a supported workflow saves successfully and triggers exactly
  one `storagePort.save` and one `agentService.reload` call (in that order).
- A definition with an unknown provider fails with `AgentValidationException` mentioning the unknown provider and
  never reaches `storagePort.save`/`agentService.reload`.
- A definition with a known provider but unsupported model fails with `AgentValidationException` mentioning the
  unsupported model and never reaches `storagePort.save`/`agentService.reload`.
- A definition with an unsupported workflow fails with `AgentValidationException` mentioning the workflow and never
  reaches `storagePort.save`/`agentService.reload`.
- A definition failing BOTH provider and workflow checks produces a single exception whose message contains both
  failure reasons.
- `updateDefinition` follows the identical validation-then-persist-then-reload contract as `saveDefinition`.
- `deleteDefinition` always calls `agentService.remove` before `storagePort.delete`, both exactly once.

## Failure modes

- Both `saveDefinition` and `updateDefinition` throw `AgentValidationException` (not `IllegalArgumentException`/
  `ChatProviderException` directly) for any validation failure. Callers only ever need to handle the one-exception
  type.

## Edge Cases

- A definition valid on every axis except an empty/blank model string. Still routed through the same
  `ChatProviderRegistry.validate` call. No special-casing here.

## Non-goals

- Does not validate `WorkflowPolicy` contents (response contract, blocked terms, etc.). Only provider/model and
  workflow variant support are checked here.
- Does not perform authorization/permission checks on who may save/update/delete a definition.
