package io.workflowai.domain.workflow.response;

public record ValidationResult(boolean valid, String reason) {

    public static ValidationResult valid(String reason) {
        return new ValidationResult(true, reason);
    }

    public static ValidationResult invalid(String reason) {
        return new ValidationResult(false, reason);
    }
}