package io.workflowai.adapter.out.chat.guardrail;

import io.workflowai.bootstrap.GuardrailProperties;
import io.workflowai.domain.workflow.GuardrailChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class GuardrailConfig {

    @Bean
    GuardrailChecker guardrailChecker(GuardrailProperties properties) {
        return new GuardrailChecker(properties.inputBlockedTerms(), properties.outputBlockedTerms());
    }
}