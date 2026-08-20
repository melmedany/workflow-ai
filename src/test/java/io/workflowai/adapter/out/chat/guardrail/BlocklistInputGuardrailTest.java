package io.workflowai.adapter.out.chat.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrailResult;
import io.workflowai.domain.workflow.GuardrailChecker;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlocklistInputGuardrailTest {

    private final GuardrailChecker guardrailChecker = mock();

    private final BlocklistInputGuardrail guardrail = new BlocklistInputGuardrail(guardrailChecker);

    @Test
    void succeedsWhenNoBlockedTermIsFound() {
        when(guardrailChecker.checkInput("hello there")).thenReturn(Optional.empty());

        InputGuardrailResult result = guardrail.validate(UserMessage.from("hello there"));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void failsWithTheMatchedTermWhenABlockedTermIsFound() {
        when(guardrailChecker.checkInput("jailbreak me")).thenReturn(Optional.of("jailbreak"));

        InputGuardrailResult result = guardrail.validate(UserMessage.from("jailbreak me"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.<InputGuardrailResult.Failure>failures().getFirst().message())
                .isEqualTo("Blocked term: jailbreak");
    }
}
