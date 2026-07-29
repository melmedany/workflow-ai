package io.workflowai.domain.conversation;

import java.io.Serializable;

public record ConversationMessage(ConversationMessageRole role, String content, boolean addToMemory) implements Serializable {}
