package io.workflowai.domain.workflow;

public final class ResponseValidationPolicy {

    private static final String BEST_EFFORT_WARNING = "\n\n> Warning: output validation failed — returning best effort result.";

    private ResponseValidationPolicy() {
    }

    public static boolean acceptsCurrentResponse(boolean validationPassed, boolean validationEnabled) {
        return validationPassed || !validationEnabled;
    }

    public static String bestEffort(String response) {
        return response + BEST_EFFORT_WARNING;
    }
}