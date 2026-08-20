package io.workflowai.adapter.out.chat.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import io.workflowai.domain.workflow.GuardrailChecker;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlocklistOutputGuardrailTest {

    private final GuardrailChecker guardrailChecker = mock();

    private final BlocklistOutputGuardrail guardrail = new BlocklistOutputGuardrail(guardrailChecker);

    @Test
    void succeedsWhenNoBlockedTermIsFound() {
        when(guardrailChecker.checkOutput("hello there")).thenReturn(Optional.empty());

        OutputGuardrailResult result = guardrail.validate(AiMessage.from("hello there"));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void failsWithTheMatchedTermWhenABlockedTermIsFound() {
        when(guardrailChecker.checkOutput("some secret")).thenReturn(Optional.of("secret"));

        OutputGuardrailResult result = guardrail.validate(AiMessage.from("some secret"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.<OutputGuardrailResult.Failure>failures().getFirst().message())
                .isEqualTo("Blocked term: secret");
    }
}
