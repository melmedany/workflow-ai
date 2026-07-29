package io.workflowai.adapter.out.chat.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import io.workflowai.domain.workflow.GuardrailChecker;
import org.springframework.stereotype.Component;

@Component
public class BlocklistOutputGuardrail implements OutputGuardrail {

    private final GuardrailChecker guardrailChecker;

    public BlocklistOutputGuardrail(GuardrailChecker guardrailChecker) {
        this.guardrailChecker = guardrailChecker;
    }

    @Override
    public OutputGuardrailResult validate(AiMessage chatResponse) {
        return guardrailChecker.checkOutput(chatResponse.text())
                .map(term -> failure("Blocked term: " + term))
                .orElseGet(this::success);
    }
}