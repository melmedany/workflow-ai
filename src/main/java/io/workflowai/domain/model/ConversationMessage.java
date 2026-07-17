package io.workflowai.domain.model;

import java.io.Serializable;

public record ConversationMessage(ConversationMessageRole role, String content) implements Serializable {}
