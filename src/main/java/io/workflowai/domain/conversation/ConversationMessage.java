package io.workflowai.domain.conversation;

public record ConversationMessage(ConversationMessageRole role, String content, boolean addToMemory) {}
