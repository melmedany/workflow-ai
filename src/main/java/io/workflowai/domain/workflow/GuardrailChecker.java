package io.workflowai.domain.workflow;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class GuardrailChecker {

    private final List<CompiledTerm> inputBlockedTerms;
    private final List<CompiledTerm> outputBlockedTerms;

    public GuardrailChecker(List<String> inputBlockedTerms, List<String> outputBlockedTerms) {
        this.inputBlockedTerms = compile(inputBlockedTerms);
        this.outputBlockedTerms = compile(outputBlockedTerms);
    }

    public Optional<String> checkInput(String text) {
        return check(text, inputBlockedTerms);
    }

    public Optional<String> checkOutput(String text) {
        return check(text, outputBlockedTerms);
    }

    private static List<CompiledTerm> compile(List<String> terms) {
        return terms.stream()
                .map(term -> new CompiledTerm(term, Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE)))
                .toList();
    }

    private Optional<String> check(String text, List<CompiledTerm> blockedTerms) {
        return blockedTerms.stream()
                .filter(term -> term.pattern().matcher(clean(text)).find())
                .map(CompiledTerm::term)
                .findFirst();
    }

    private String clean(String text) {
        if (text == null) return "";

        // Simple trivial checks
        return text
                .toLowerCase()
                .replaceAll("\\s+", " ").trim();
    }

    private record CompiledTerm(String term, Pattern pattern) {
    }
}
