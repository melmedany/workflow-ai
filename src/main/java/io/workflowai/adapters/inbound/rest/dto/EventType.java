package io.workflowai.adapters.inbound.rest.dto;

public enum EventType {
    CONVERSATION_CREATED,
    DECISION,
    TOKEN,
    RESPONSE_COMPLETED,
    MEMORY_UPDATED,
    CONVERSATION_COMPLETED,
    ERROR,
    STAGE
}
