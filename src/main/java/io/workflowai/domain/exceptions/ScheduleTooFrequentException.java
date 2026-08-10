package io.workflowai.domain.exceptions;

public class ScheduleTooFrequentException extends DomainException {

    public ScheduleTooFrequentException(String message) {
        super(message);
    }
}