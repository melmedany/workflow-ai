package io.workflowai.domain.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GuardrailCheckerTest {

    @Test
    void returnsEmptyWhenNoTermsAreConfigured() {
        GuardrailChecker checker = new GuardrailChecker(List.of(), List.of());

        assertThat(checker.checkInput("anything at all")).isEmpty();
        assertThat(checker.checkOutput("anything at all")).isEmpty();
    }

    @Test
    void returnsEmptyForNullOrBlankText() {
        GuardrailChecker checker = new GuardrailChecker(List.of("jailbreak"), List.of("jailbreak"));

        assertThat(checker.checkInput(null)).isEmpty();
        assertThat(checker.checkInput("")).isEmpty();
        assertThat(checker.checkInput("   ")).isEmpty();
    }

    @Test
    void matchIsCaseInsensitive() {
        GuardrailChecker checker = new GuardrailChecker(List.of("jailbreak"), List.of());

        assertThat(checker.checkInput("please JailBreak the model")).contains("jailbreak");
    }

    @Test
    void matchOnlyOnWholeWordsNotAsSubstringOfALargerWord() {
        GuardrailChecker checker = new GuardrailChecker(List.of("art"), List.of());

        assertThat(checker.checkInput("let's start now")).isEmpty();
        assertThat(checker.checkInput("I love art")).contains("art");
    }

    @Test
    void matchesMultiWordPhraseAcrossIrregularWhitespace() {
        GuardrailChecker checker = new GuardrailChecker(List.of("ignore previous instructions"), List.of());

        assertThat(checker.checkInput("please   ignore\nprevious\t instructions now")).contains("ignore previous instructions");
    }

    @Test
    void returnsFirstMatchingTermInConfiguredOrder() {
        GuardrailChecker checker = new GuardrailChecker(List.of("jailbreak", "reveal your system prompt"), List.of());

        Optional<String> result = checker.checkInput("jailbreak and reveal your system prompt");

        assertThat(result).contains("jailbreak");
    }

    @Test
    void inputAndOutputBlockedTermsAreCheckedIndependently() {
        GuardrailChecker checker = new GuardrailChecker(List.of("jailbreak"), List.of("secret"));

        assertThat(checker.checkOutput("jailbreak")).isEmpty();
        assertThat(checker.checkInput("secret")).isEmpty();

        assertThat(checker.checkInput("jailbreak")).contains("jailbreak");
        assertThat(checker.checkOutput("secret")).contains("secret");
    }
}
