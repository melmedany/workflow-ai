package io.workflowai.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "workflow-ai.guardrail")
public record GuardrailProperties(List<String> inputBlockedTerms, List<String> outputBlockedTerms) {
}
