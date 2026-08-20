package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.ResponseValidator;
import io.workflowai.domain.workflow.WorkflowStage;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.exceptions.GuardrailBlockedException;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.response.ValidationResult;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.workflowai.application.execution.workflow.WorkflowPrompts.withResponseContractInstructions;

public class ExecuteWorkflowStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(ExecuteWorkflowStage.class);

    private final ChatProviderRegistry chatProviderRegistry;
    private final ResponseValidator responseValidator;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public ExecuteWorkflowStage(ChatProviderRegistry chatProviderRegistry, ResponseValidator responseValidator,
                                List<WorkflowEventStreamer> workflowEventStreamers) {
        this.chatProviderRegistry = chatProviderRegistry;
        this.responseValidator = responseValidator;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.EXECUTE_WORKFLOW;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.EXECUTE_WORKFLOW));

        AgentProperties agentProperties = state.agentProperties();
        ChatCompletionRequest request = new ChatCompletionRequest(
                agentProperties.model(),
                agentProperties.temperature(),
                withResponseContractInstructions(state.systemPrompt(), agentProperties.workflowPolicy().responseContract()),
                state.userMessage(),
                state.memoryContext());

        log.debug("[{}] Starting call with model [{}]", agentProperties.id(), agentProperties.model());

        String response;
        try {
            // Buffered, not streamed live. Note this is only a *draft*. SELF_VERIFICATION decides
            // whether it's final or needs a retry, so persist/stream must not happen here.
            response = chatProviderRegistry.get(agentProperties.chatProviderId()).stream(request, _ -> {
            });
        } catch (GuardrailBlockedException ex) {
            // Input was blocked at the provider boundary. A retry through SELF_VERIFICATION would
            // hit the exact same block, so short-circuit straight to a final, already-safe response.
            log.warn("[{}] Input guardrail blocked {} request - returning fallback", state.triggerSource(), agentProperties.id());
            workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.EXECUTE_WORKFLOW));
            return Map.of(
                    WorkflowState.KEY_GENERATED_RESPONSE, agentProperties.workflowPolicy().failedToProcessMessage(),
                    WorkflowState.KEY_VALIDATION_PASSED, true);
        }

        ValidationResult validation = responseValidator
                .validate(agentProperties.workflowPolicy().responseContract(), response);
        if (!validation.valid()) {
            log.warn("[{}] Generated response failed validation: {}", agentProperties.id(), validation.reason());
        }

        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.EXECUTE_WORKFLOW));
        log.debug("[{}] Chat streaming complete, length: {}", agentProperties.id(), response.length());

        return Map.of(
                WorkflowState.KEY_GENERATED_RESPONSE, response,
                WorkflowState.KEY_VALIDATION_PASSED, validation.valid(),
                WorkflowState.KEY_VALIDATION_FAILURE_REASON, validation.reason() == null ? "" : validation.reason()
        );
    }
}