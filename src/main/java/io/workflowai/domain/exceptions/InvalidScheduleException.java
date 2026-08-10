package io.workflowai.domain.exceptions;

public class InvalidScheduleException extends DomainException {

    public InvalidScheduleException(String message) {
        this(message, null);
    }

    public InvalidScheduleException(String message, Throwable cause) {
        super(message, cause);
    }
}