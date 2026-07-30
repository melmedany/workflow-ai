package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.ResponseValidator;
import io.workflowai.domain.workflow.WorkflowStage;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.execution.workflow.WorkflowPrompts;
import io.workflowai.domain.workflow.response.ValidationResult;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.workflowai.application.execution.workflow.WorkflowPrompts.withResponseContractInstructions;

public class SelfVerificationStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(SelfVerificationStage.class);

    private final ChatProviderRegistry chatProviderRegistry;
    private final ResponseValidator responseValidator;
    private final PersistResponseStage persistResponseStage;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public SelfVerificationStage(ChatProviderRegistry chatProviderRegistry, ResponseValidator responseValidator,
                                 PersistResponseStage persistResponseStage, List<WorkflowEventStreamer> workflowEventStreamers) {
        this.chatProviderRegistry = chatProviderRegistry;
        this.responseValidator = responseValidator;
        this.persistResponseStage = persistResponseStage;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.SELF_VERIFICATION;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.SELF_VERIFICATION));
        AgentProperties agentProperties = state.agentProperties();

        if (state.validationPassed()) {
            workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.SELF_VERIFICATION));
            String finalResponse = persistResponseStage.finalizeResponse(state, state.generatedResponse().orElse(""));
            return Map.of(WorkflowState.KEY_GENERATED_RESPONSE, finalResponse, WorkflowState.KEY_VALIDATION_PASSED, true);
        }

        if (state.retried()) {
            log.warn("[{}] Self-verification failed twice ({}) — returning best effort",
                    agentProperties.id(), state.validationFailureReason());
            String failureReason = "Response still invalid after retry (%s) — returning best effort".formatted(state.validationFailureReason());
            workflowEventStreamers.forEach(s -> s.stageFailed(state.runId(), StageId.SELF_VERIFICATION, failureReason));
            String finalResponse = persistResponseStage.finalizeResponse(state, state.generatedResponse().orElse(""));
            return Map.of(WorkflowState.KEY_GENERATED_RESPONSE, finalResponse, WorkflowState.KEY_VALIDATION_PASSED, true);
        }

        log.debug("[{}] Self-verification failed ({}) — attempting one retry",
                agentProperties.id(), state.validationFailureReason());

        String retryPrompt = WorkflowPrompts.retryPrompt(state.userMessage(), state.generatedResponse().orElse(""), state.validationFailureReason());
        ChatCompletionRequest retryRequest = new ChatCompletionRequest(
                agentProperties.model(), agentProperties.temperature(),
                withResponseContractInstructions(state.systemPrompt(), agentProperties.workflowPolicyProperties().responseContract()),
                retryPrompt, state.memoryContext());

        // Buffered for the same reason as EXECUTE_WORKFLOW: this must be the complete retry text
        // before it can be re-validated below.
        String retryResponse = chatProviderRegistry.get(agentProperties.chatProviderId()).stream(retryRequest, _ -> {
        });

        ValidationResult retryValidation = responseValidator
                .validate(agentProperties.workflowPolicyProperties().responseContract(), retryResponse);

        if (retryValidation.valid()) {
            log.debug("[{}] Retry passed validation", agentProperties.id());
            workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.SELF_VERIFICATION));
        } else {
            log.warn("[{}] Retry still invalid ({}) — returning latest retry result", agentProperties.id(), retryValidation.reason());
            workflowEventStreamers.forEach(s -> s.stageFailed(state.runId(), StageId.SELF_VERIFICATION,
                    "Retry still invalid (%s) — returning latest retry result".formatted(retryValidation.reason())));
        }

        String finalResponse = persistResponseStage.finalizeResponse(state, retryResponse);
        return Map.of(WorkflowState.KEY_GENERATED_RESPONSE, finalResponse,
                WorkflowState.KEY_VALIDATION_PASSED, true,
                WorkflowState.KEY_RETRIED, true);
    }
}