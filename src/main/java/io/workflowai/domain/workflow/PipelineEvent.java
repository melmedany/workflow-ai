package io.workflowai.domain.workflow;

public sealed interface PipelineEvent {

    record StageStarted(StageId stageId, String label) implements PipelineEvent {}

    record StageCompleted(StageId stageId, String label) implements PipelineEvent {}

    record StageFailed(StageId stageId, String label, String reason) implements PipelineEvent {}

    record DecisionMade(DecisionMode mode, String reason) implements PipelineEvent {}

    record Token(String token) implements PipelineEvent {}

    record ResponseCompleted(String fullResponse) implements PipelineEvent {}

    record MemoryUpdated() implements PipelineEvent {}

    record ConversationCompleted() implements PipelineEvent {}

    record Error(String message) implements PipelineEvent {}
}
