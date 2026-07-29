package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.StageSettings;
import io.workflowai.application.execution.WorkflowState;
import io.workflowai.application.port.out.ChatRequest;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.execution.WorkflowPrompts;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class GenerateClarificationStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(GenerateClarificationStage.class);

    private final ChatProviderRegistry chatProviderRegistry;
    private final StageSettings stagesProperties;
    private final PersistResponseStage persistResponseStage;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public GenerateClarificationStage(ChatProviderRegistry chatProviderRegistry, StageSettings stagesProperties,
                                      PersistResponseStage persistResponseStage, List<WorkflowEventStreamer> workflowEventStreamers) {
        this.chatProviderRegistry = chatProviderRegistry;
        this.stagesProperties = stagesProperties;
        this.persistResponseStage = persistResponseStage;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.GENERATE_CLARIFICATION;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.GENERATE_CLARIFICATION));

        String clarification = state.routingDecision()
                .map(RoutingDecision::clarificationQuestion)
                .filter(q -> !q.isBlank())
                .orElseGet(() -> {
                    log.debug("[{}] No clarification question from classifier, generating via chat provider", state.agentProperties().id());
                    return executeGenerateClarification(state);
                });

        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.GENERATE_CLARIFICATION));
        String finalResponse = persistResponseStage.finalizeResponse(state, clarification);
        log.debug("[{}] Clarification question generated", state.agentProperties().id());

        return Map.of(WorkflowState.KEY_GENERATED_RESPONSE, finalResponse);
    }

    private String executeGenerateClarification(WorkflowState state) {
        StageSettings.StageSetting stageProperties = stagesProperties.get(StageId.GENERATE_CLARIFICATION);
        String prompt = WorkflowPrompts.clarificationPrompt(state.userMessage());
        ChatRequest request = new ChatRequest(stageProperties.model(), stageProperties.temperature(), state.systemPrompt(), prompt, state.memoryContext());
        return chatProviderRegistry.get(stageProperties.chatProviderId()).call(request);
    }
}