package io.workflowai.domain.model;

import java.util.List;

public record LlmRequest(
        String model,
        double temperature,
        String systemPrompt,
        String userMessage,
        List<ConversationMessage> history) {
}
