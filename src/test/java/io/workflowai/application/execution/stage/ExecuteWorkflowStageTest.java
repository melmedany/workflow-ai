package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.ResponseValidator;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.exceptions.GuardrailBlockedException;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.domain.workflow.response.ResponseContract;
import io.workflowai.domain.workflow.response.ResponseFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecuteWorkflowStageTest {

    private final ChatProvider provider = mock();
    private final WorkflowEventStreamer streamer = mock();
    private final ResponseValidator responseValidator = new ResponseValidator(JsonMapper.builder().build());

    @BeforeEach
    void setUp() {
        when(provider.getId()).thenReturn(ChatProviderId.Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);
    }

    @Test
    void successfulResponsePassingValidationIsReturnedAsIs() {
        when(provider.stream(any(ChatCompletionRequest.class), any())).thenReturn("a helpful answer");

        WorkflowState state = StagesUtil.state(ResponseContract.text());

        Map<String, Object> result = stage().execute(state);

        assertThat(result)
                .containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "a helpful answer")
                .containsEntry(WorkflowState.KEY_VALIDATION_PASSED, true)
                .containsEntry(WorkflowState.KEY_VALIDATION_FAILURE_REASON, "");

        verify(streamer).stageStarted(state.runId(), StageId.EXECUTE_WORKFLOW);
        verify(streamer).stageCompleted(state.runId(), StageId.EXECUTE_WORKFLOW);
    }

    @Test
    void responseFailingValidationIsStillReturnedWithFailureFlagged() {
        ResponseContract tooShort = new ResponseContract(ResponseFormat.TEXT, List.of(), 1000);
        when(provider.stream(any(ChatCompletionRequest.class), any())).thenReturn("short");

        WorkflowState state = StagesUtil.state(tooShort);

        Map<String, Object> result = stage().execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "short");
        assertThat(result).containsEntry(WorkflowState.KEY_VALIDATION_PASSED, false);
        assertThat((String) result.get(WorkflowState.KEY_VALIDATION_FAILURE_REASON)).isNotBlank();
    }

    @Test
    void guardrailBlockedInputFallsBackToPolicyFailureMessage() {
        when(provider.stream(any(ChatCompletionRequest.class), any()))
                .thenThrow(new GuardrailBlockedException(ChatProviderId.Ollama, "Input blocked by guardrail"));

        WorkflowState state = StagesUtil.state(ResponseContract.text());

        Map<String, Object> result = stage().execute(state);

        assertThat(result)
                .containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "fallback")
                .containsEntry(WorkflowState.KEY_VALIDATION_PASSED, true);
    }

    @Test
    void stageCompletedIsEmittedEvenWhenInputIsGuardrailBlocked() {
        when(provider.stream(any(ChatCompletionRequest.class), any()))
                .thenThrow(new GuardrailBlockedException(ChatProviderId.Ollama, "Input blocked by guardrail"));

        WorkflowState state = StagesUtil.state(ResponseContract.text());

        stage().execute(state);

        verify(streamer).stageStarted(state.runId(), StageId.EXECUTE_WORKFLOW);
        verify(streamer).stageCompleted(state.runId(), StageId.EXECUTE_WORKFLOW);
    }

    private ExecuteWorkflowStage stage() {
        return new ExecuteWorkflowStage(
                new ChatProviderRegistry(List.of(provider)),
                responseValidator,
                List.of(streamer));
    }
}
