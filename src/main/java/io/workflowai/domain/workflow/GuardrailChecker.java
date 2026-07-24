package io.workflowai.domain.workflow;

import io.workflowai.application.GuardrailProperties;

import java.util.List;
import java.util.Optional;

public class GuardrailChecker {

    private final List<String> inputBlockedTerms;
    private final List<String> outputBlockedTerms;

    public GuardrailChecker(GuardrailProperties properties) {
        this.inputBlockedTerms = properties.inputBlockedTerms().stream().map(String::toLowerCase).toList();
        this.outputBlockedTerms = properties.outputBlockedTerms().stream().map(String::toLowerCase).toList();
    }

    public Optional<String> checkInput(String text) {
        return check(text, inputBlockedTerms);
    }

    public Optional<String> checkOutput(String text) {
        return check(text, outputBlockedTerms);
    }

    // TODO: should do pattern matching for words not whole sentences
    private Optional<String> check(String text, List<String> blockedTerms) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = text.toLowerCase();
        return blockedTerms.stream().filter(normalized::contains).findFirst();
    }


}
