package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.workflowai.application.execution.stage.StageSettings.StageSetting;
import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompactMemoryStageTest {

    private final ChatProvider provider = mock();
    private final AgentMemoryStorage agentMemoryStorage = mock();
    private final WorkflowEventStreamer streamer = mock();

    @Test
    void memoryDisabledSkipsCompactionEntirely() {
        WorkflowState state = StagesUtil.state(false, "Summarize open PRs", "Here is the summary");

        stage().execute(state);

        verifyNoInteractions(agentMemoryStorage);
        verify(provider, never()).call(any(ChatCompletionRequest.class));
    }

    @Test
    void memoryEnabledWithBlankResponseSkipsCompaction() {
        WorkflowState state = StagesUtil.state(true, "Summarize open PRs", "");

        stage().execute(state);

        verifyNoInteractions(agentMemoryStorage);
        verify(provider, never()).call(any(ChatCompletionRequest.class));
    }

    @Test
    void memoryEnabledWithResponseReplacesStoredMemory() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn("user prefers concise summaries");

        WorkflowState state = StagesUtil.state(true, "Summarize open PRs", "Here is the summary");

        stage().execute(state);

        verify(agentMemoryStorage).replace(state.conversationId(), state.agentProperties().id(),
                "user prefers concise summaries");
    }

    @Test
    void blankCompactionResultDoesNotReplaceStoredMemory() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn("   ");

        WorkflowState state = StagesUtil.state(true, "Summarize open PRs", "Here is the summary");

        stage().execute(state);

        verify(agentMemoryStorage, never()).replace(any(), any(), anyString());
    }

    @Test
    void providerFailureIsSwallowedAndDoesNotPropagate() {
        when(provider.call(any(ChatCompletionRequest.class))).thenThrow(new RuntimeException("boom"));

        WorkflowState state = StagesUtil.state(true, "Summarize open PRs", "Here is the summary");

        stage().execute(state);

        verify(agentMemoryStorage, never()).replace(any(), any(), anyString());
    }

    @Test
    void emitsStageStartedAndStageCompletedWhenMemoryEnabled() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn("compacted memory");

        WorkflowState state = StagesUtil.state(true, "Summarize open PRs", "Here is the summary");

        stage().execute(state);

        verify(streamer).stageStarted(state.runId(), StageId.COMPACT_MEMORY);
        verify(streamer).stageCompleted(state.runId(), StageId.COMPACT_MEMORY);
    }

    @Test
    void stageStartedAndStageCompletedNotEmittedWhenMemoryDisabled() {
        WorkflowState state = StagesUtil.state(false, "Summarize open PRs", "Here is the summary");

        stage().execute(state);

        verify(streamer, never()).stageStarted(state.runId(), StageId.COMPACT_MEMORY);
        verify(streamer, never()).stageCompleted(state.runId(), StageId.COMPACT_MEMORY);
    }

    private CompactMemoryStage stage() {
        when(provider.getId()).thenReturn(Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);

        StageSettings settings = new StageSettings(List.of(
                new StageSetting(StageId.COMPACT_MEMORY, Ollama, "memory-model", 0.2)));

        return new CompactMemoryStage(new ChatProviderRegistry(List.of(provider)), settings,
                agentMemoryStorage, List.of(streamer));
    }
}
