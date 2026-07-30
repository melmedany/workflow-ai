package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.workflow.StageId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared by every stage that turns a classification decision into a short generated response
 * (GENERATE_REDIRECT/GREETING/REFUSAL). The model call already applies the output guardrail at the
 * {@link io.workflowai.application.port.out.ChatProvider} boundary, so a failure here is only ever an infra/model failure.
 */
public class DecisionResponseGenerator {

    private static final Logger log = LoggerFactory.getLogger(DecisionResponseGenerator.class);

    private final ChatProviderRegistry chatProviderRegistry;
    private final StageSettings stagesProperties;

    public DecisionResponseGenerator(ChatProviderRegistry chatProviderRegistry, StageSettings stagesProperties) {
        this.chatProviderRegistry = chatProviderRegistry;
        this.stagesProperties = stagesProperties;
    }

    String generate(WorkflowState state, StageId stageId, String prompt) {
        StageSettings.StageSetting stageProperties = stagesProperties.get(stageId);
        try {
            ChatCompletionRequest request = new ChatCompletionRequest(stageProperties.model(), stageProperties.temperature(), state.systemPrompt(), prompt, state.memoryContext());
            return chatProviderRegistry.get(stageProperties.chatProviderId()).stream(request, _ -> {
            });
        } catch (Exception ex) {
            log.warn("[{}] Decision response generation failed, using fallback: {}", state.agentProperties().id(), ex.getMessage());
            return state.agentProperties().workflowPolicyProperties().failedToProcessMessage();
        }
    }
}