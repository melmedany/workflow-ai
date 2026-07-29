package io.workflowai.domain.workflow;

public sealed interface WorkflowEvent {

    record StageStarted(StageId stageId, String label) implements WorkflowEvent {}

    record StageCompleted(StageId stageId, String label) implements WorkflowEvent {}

    record StageFailed(StageId stageId, String label, String reason) implements WorkflowEvent {}

    record DecisionMade(DecisionMode mode, String reason) implements WorkflowEvent {}

    record Token(String token) implements WorkflowEvent {}

    record ResponseCompleted(String fullResponse) implements WorkflowEvent {}

    record MemoryUpdated() implements WorkflowEvent {}

    record ConversationCompleted() implements WorkflowEvent {}

    record Error(String message) implements WorkflowEvent {}
}
