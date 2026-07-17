package io.workflowai.domain.model;

import java.util.UUID;

public record AgentRequest(String message, UUID conversationId) {}
