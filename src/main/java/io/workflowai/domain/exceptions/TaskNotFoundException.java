package io.workflowai.domain.exceptions;

import java.util.UUID;

public class TaskNotFoundException extends DomainException {

    public TaskNotFoundException(UUID taskId) {
        super("Scheduled task not found: %s".formatted(taskId));
    }
}