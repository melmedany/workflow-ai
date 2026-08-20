package io.workflowai.application.execution.stage;

import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LoadMemoryStageTest {

    private final AgentMemoryStorage agentMemoryStorage = mock();
    private final WorkflowEventStreamer streamer = mock();

    private final LoadMemoryStage stage = new LoadMemoryStage(agentMemoryStorage, List.of(streamer));

    @Test
    void memoryDisabledSkipsStorageAndReturnsEmptyContext() {
        WorkflowState state = StagesUtil.state(false);

        Map<String, Object> result = stage.execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_MEMORY_CONTEXT, "");
        verifyNoInteractions(agentMemoryStorage);

        verify(streamer, never()).stageStarted(state.runId(), StageId.LOAD_MEMORY);
        verify(streamer, never()).stageCompleted(state.runId(), StageId.LOAD_MEMORY);
    }

    @Test
    void memoryEnabledReturnsStoredMemory() {
        WorkflowState state = StagesUtil.state(true);

        when(agentMemoryStorage.getMemory(state.conversationId(), state.agentProperties().id()))
                .thenReturn(Optional.of("user prefers concise answers"));

        Map<String, Object> result = stage.execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_MEMORY_CONTEXT, "user prefers concise answers");
    }

    @Test
    void memoryEnabledWithNoStoredMemoryReturnsEmptyContext() {
        WorkflowState state = StagesUtil.state(true);

        when(agentMemoryStorage.getMemory(state.conversationId(), state.agentProperties().id()))
                .thenReturn(Optional.empty());

        Map<String, Object> result = stage.execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_MEMORY_CONTEXT, "");
    }
}
