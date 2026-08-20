package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.ResponseValidator;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.domain.workflow.response.ResponseContract;
import io.workflowai.domain.workflow.response.ResponseFormat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfVerificationStageTest {

    private final ChatProvider provider = mock();
    private final ConversationMessageStorage messages = mock();
    private final WorkflowEventStreamer streamer = mock();
    private final ResponseValidator responseValidator = new ResponseValidator(JsonMapper.builder().build());

    @Test
    void alreadyValidDraftIsPersistedWithoutCallingTheProvider() {
        WorkflowState state = StagesUtil.state(true, false, "a valid answer", ResponseContract.text());

        Map<String, Object> result = stage().execute(state);

        assertThat(result)
                .containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "a valid answer")
                .containsEntry(WorkflowState.KEY_VALIDATION_PASSED, true);
        verify(provider, never()).stream(any(ChatCompletionRequest.class), any());
        verify(streamer).stageCompleted(state.runId(), StageId.SELF_VERIFICATION);
    }

    @Test
    void invalidDraftRetriesOnceAndAcceptsAValidRetry() {
        ResponseContract tooShort = new ResponseContract(ResponseFormat.TEXT, List.of(), 5);
        when(provider.stream(any(ChatCompletionRequest.class), any())).thenReturn("a corrected longer answer");

        WorkflowState state = StagesUtil.state(false, false, "no", tooShort);

        Map<String, Object> result = stage().execute(state);

        assertThat(result)
                .containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "a corrected longer answer")
                .containsEntry(WorkflowState.KEY_VALIDATION_PASSED, true)
                .containsEntry(WorkflowState.KEY_RETRIED, true);

        verify(streamer).stageStarted(state.runId(), StageId.SELF_VERIFICATION);
        verify(streamer).stageCompleted(state.runId(), StageId.SELF_VERIFICATION);
    }

    @Test
    void invalidDraftRetriesOnceAndAcceptsBestEffortWhenRetryIsStillInvalid() {
        ResponseContract tooShort = new ResponseContract(ResponseFormat.TEXT, List.of(), 1000);
        when(provider.stream(any(ChatCompletionRequest.class), any())).thenReturn("still short");

        WorkflowState state = StagesUtil.state(false, false, "no", tooShort);

        Map<String, Object> result = stage().execute(state);

        assertThat(result)
                .containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "still short")
                .containsEntry(WorkflowState.KEY_VALIDATION_PASSED, true)
                .containsEntry(WorkflowState.KEY_RETRIED, true);

        verify(streamer).stageFailed(eq(state.runId()), eq(StageId.SELF_VERIFICATION), anyString());
        verify(streamer, never()).stageCompleted(state.runId(), StageId.SELF_VERIFICATION);
    }

    @Test
    void onlyOneRetryIsEverAttempted() {
        ResponseContract tooShort = new ResponseContract(ResponseFormat.TEXT, List.of(), 1000);
        when(provider.stream(any(ChatCompletionRequest.class), any())).thenReturn("still short");

        WorkflowState state = StagesUtil.state(false, false, "no", tooShort);

        stage().execute(state);

        verify(provider, times(1)).stream(any(ChatCompletionRequest.class), any());
    }

    private SelfVerificationStage stage() {
        when(provider.getId()).thenReturn(ChatProviderId.Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);

        return new SelfVerificationStage(
                new ChatProviderRegistry(List.of(provider)),
                responseValidator,
                new PersistResponseStage(messages, List.of()),
                List.of(streamer));
    }
}
