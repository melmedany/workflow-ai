# Agent definition management (AgentDefinitionUseCase)

## Purpose

Ensure that only runnable agent definitions are stored and that the active agent registry reflects every successful
definition change.

## Requirements

- Saving or updating a definition must independently validate that the selected chat provider exists, the selected model
  is supported, and the selected workflow mode is supported.
- All validation failures found for one definition must be reported together. A caller must not need to fix one field
  before learning that another field is also invalid.
- A definition that fails validation must not be stored and must not change the active agent registry.
- After a definition is saved or updated successfully, the corresponding active agent must be reloaded from the
  persisted definition before the operation is considered complete.
- Deleting a definition must first make the agent unavailable for new runs and then remove the persisted definition.
- Listing definitions, retrieving one definition, retrieving an agent's workflow diagram, and listing supported chat
  providers must not modify definitions or active agents.
- This validation covers provider, model, and workflow support. It does not define validation of workflow-policy
  contents or authorization rules.

## Failure behaviour

- Provider, model, and workflow validation errors must be exposed through one agent-definition validation failure,
  regardless of which underlying check found them.
- A failed persistence operation must not be reported as a successful save or update.
- A definition must not be reported as deleted while the agent remains available for new runs.

## Acceptance criteria

- A valid definition is persisted, and the active agent is reloaded using the persisted definition's identity.
- A definition with an unknown provider, an unsupported model, or an unsupported workflow is rejected without any
  persistence or active-registry change.
- A definition that fails both provider/model and workflow validation reports both reasons in one failure.
- Updating a definition has the same validation and activation guarantees as creating one.
- Once deletion succeeds, the agent is unavailable for new runs and its stored definition no longer exists.
