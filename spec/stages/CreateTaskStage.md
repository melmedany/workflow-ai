# CreateTaskStage

## Purpose

Turn an `EXECUTE_SCHEDULE` routing decision into a standing `ConversationTask` (create or update), confirm it to the
user in plain language, or fall back to `CLARIFY`/`REFUSE` when the request can't safely produce a task.

## Inputs / constraints / preconditions

- Reads from `WorkflowState`: `runId()`, `triggerSource()`, `agentProperties().id()`, `conversationId()`,
  `userMessage()`, and `routingDecision()`.
- Requires `routingDecision()` to be present, reaching this stage without one is a workflow-wiring error, not a
  user-input error.
- Depends on `TaskUseCase.createOrUpdate(agentId, conversationId, instruction, scheduleType, startDateTime,
  duration)` and on `PersistResponseStage` to persist/emit the confirmation text.

## Outputs / constraints / postconditions / invariants

- A run whose `triggerSource()` is `SYSTEM_TRIGGER` NEVER creates or updates a task: it always returns a `REFUSE`
  decision with reason `"A scheduled run cannot create another scheduled task"` and `extractedIntent` =
  `state.userMessage()`, without any `TaskUseCase` interaction. This is a hard invariant preventing scheduled runs
  from spawning further scheduled runs.
- The instruction actually sent to `TaskUseCase` is `SchedulingIntentDetector.cleanInstruction(decision
  .scheduleInstruction())`, not the raw decision field.
- When the cleaned instruction is `null` or blank (including when cleaning strips the text down to nothing, e.g. an
  instruction that was pure scheduling/command wording), the stage returns `REFUSE` with reason `"Task instruction
  cannot be extracted or schedule other tasks"` and `extractedIntent` = `decision.extractedIntent()`, no
  `TaskUseCase` call is made.
- On a successful `TaskUseCase.createOrUpdate`, the stage persists and emits a confirmation message (via
  `PersistResponseStage.finalizeResponse`) describing the task's instruction, and returns
  `KEY_GENERATED_RESPONSE` = that confirmation and `KEY_VALIDATION_PASSED = true`.
- A schedule that cannot be parsed (invalid/missing `scheduleType`, unparsable `startDateTime`, or unparsable
  `duration`) results in `CLARIFY`, with `reason` = the underlying error message, `extractedIntent` =
  `decision.extractedIntent()`, and a fixed clarifying question asking the user to restate the schedule. This
  includes the case where the decision doesn't carry usable scheduling fields at all. A missing/unparsable field is
  treated as "the schedule could not be parsed", the same failure category as a malformed one.
- A schedule rejected by policy as too frequent (`ScheduleTooFrequentException`) results in `REFUSE`, with `reason` =
  the exception's message and `extractedIntent` = `decision.extractedIntent()`.
- Emits `stageStarted(runId, CREATE_TASK)` at the very start of every execution, and `stageCompleted(runId,
  CREATE_TASK)` exactly once on every outcome path (system-trigger refusal, instruction-extraction refusal, success,
  clarify, and too-frequent refusal).

## Interfaces

- `StageId stageId()` -> `StageId.CREATE_TASK`
- `Map<String, Object> execute(WorkflowState state)`

## Acceptance criteria

- `SYSTEM_TRIGGER` run -> `REFUSE`, zero `TaskUseCase` interactions, `stageCompleted` still emitted.
- `null`/blank cleaned instruction -> `REFUSE` with the instruction-extraction reason, zero `TaskUseCase`
  interactions.
- Valid `RECURRING` or `ONCE` schedule -> a task created/updated with the exact cleaned instruction, schedule type,
  parsed start time, and duration. Response contains the task's instruction `KEY_VALIDATION_PASSED = true`.
- `TaskUseCase` throwing `DateTimeParseException`, `IllegalArgumentException`, or `InvalidScheduleException` ->
  `CLARIFY` with a non-blank `clarificationQuestion`.
- A decision missing a usable `scheduleType` or `startDateTime` (e.g. `null`) is treated the same as an
  unparsable one and produces `CLARIFY` rather than propagating an exception.
- `TaskUseCase` throwing `ScheduleTooFrequentException` -> `REFUSE` with the exception's message as the reason.
- Every outcome emits exactly one `stageStarted` and one `stageCompleted` for `CREATE_TASK`.

## Failure modes

- Missing `routingDecision()` on the state -> `IllegalStateException("CREATE_TASK reached without a routing
  decision")`. Not a normal user-facing outcome, indicates a workflow graph bug.
- `DateTimeParseException` / `IllegalArgumentException` / `InvalidScheduleException` from `TaskUseCase` -> converted
  to `CLARIFY`.
- `ScheduleTooFrequentException` from `TaskUseCase` -> converted to `REFUSE`.
- Missing/`null` scheduling fields on the decision -> intended to be handled the same as a parse failure (`CLARIFY`),
  not to crash the stage.

## Edge Cases

- An instruction that, after cleaning, becomes an empty string (as opposed to `null`), treated the same as `null`
  (blank check).
- An instruction that still contains scheduling-sounding wording after cleaning (e.g. "every hour, ping me" when the
  detector doesn't fully strip it) still submitted to `TaskUseCase` as-is. This stage does not re-validate content,
  only presence.

## Non-goals

- Does not itself decide `ONCE` vs `RECURRING` or compute the schedule, those come from the classifier's decision.
- Does not talk to the scheduler/JobRunr directly, that is `TaskUseCase`'s and its adapters' responsibility.
- Does not deduplicate tasks by intent key, that logic lives in `TaskUseCase`/`TaskService`.
