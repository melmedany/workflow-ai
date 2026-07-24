package io.workflowai.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "workflow-ai.guardrails")
public record GuardrailProperties(List<String> inputBlockedTerms, List<String> outputBlockedTerms) {
}
