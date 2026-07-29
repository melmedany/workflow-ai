package io.workflowai.adapter.out.chat.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import io.workflowai.domain.workflow.GuardrailChecker;
import org.springframework.stereotype.Component;

@Component
public class BlocklistInputGuardrail implements InputGuardrail {

    private final GuardrailChecker guardrailChecker;

    public BlocklistInputGuardrail(GuardrailChecker guardrailChecker) {
        this.guardrailChecker = guardrailChecker;
    }

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        return guardrailChecker.checkInput(userMessage.singleText())
                .map(term -> failure("Blocked term: " + term))
                .orElseGet(this::success);
    }
}