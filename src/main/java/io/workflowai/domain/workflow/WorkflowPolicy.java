package io.workflowai.domain.workflow;

import java.util.List;
import java.util.Random;

public record WorkflowPolicy(
        List<String> supportedCapabilities,
        List<String> greetings,
        List<String> refuseMessages,
        List<String> redirectMessages,
        int maxRetries,
        boolean strictValidation) {

    private static final Random random = new Random();

    public String greeting() {
        return greetings.get(random.nextInt(greetings.size()));
    }

    public String refuseMessage() {
        return refuseMessages.get(random.nextInt(refuseMessages.size()));
    }

    public String redirectMessage() {
        return redirectMessages.get(random.nextInt(redirectMessages.size()));
    }
}
