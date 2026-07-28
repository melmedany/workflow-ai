package io.workflowai.domain.workflow;

import io.workflowai.application.GuardrailProperties;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

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

    private Optional<String> check(String text, List<String> blockedTerms) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return blockedTerms.stream()
                // TODO: this is very inefficient, compiling terms with evey text to check. Consider a more efficient way (recompiling is not a good way as well)
                .filter(term -> Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE)
                        .matcher(text)
                        .find())
                .findFirst();
    }


}
