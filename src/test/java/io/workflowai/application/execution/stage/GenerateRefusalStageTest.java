package io.workflowai.application.execution.stage;

import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateRefusalStageTest {

    private final DecisionResponseGenerator generator = mock();
    private final ConversationMessageStorage messages = mock();
    private final WorkflowEventStreamer streamer = mock();

    private final GenerateRefusalStage stage = new GenerateRefusalStage(
            generator, new PersistResponseStage(messages, List.of()), List.of(streamer));

    @Test
    void generatesAndPersistsTheRefusal() {
        when(generator.generate(any(WorkflowState.class), eq(StageId.GENERATE_REFUSAL), anyString()))
                .thenReturn("Sorry, I can't help with that.");

        WorkflowState state = StagesUtil.state(RoutingDecision.refuse("refusal", "request"));

        Map<String, Object> result = stage.execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "Sorry, I can't help with that.");
        verify(messages).save(any(UUID.class), any(UUID.class),
                argThat(message -> message.content().equals("Sorry, I can't help with that.")));
    }

    @Test
    void synthesizesADefaultRefuseDecisionWhenNoneIsPresent() {
        when(generator.generate(any(WorkflowState.class), eq(StageId.GENERATE_REFUSAL), anyString()))
                .thenReturn("Sorry, I can't help with that.");

        WorkflowState state = new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "unsafe request",
                WorkflowState.KEY_SYSTEM_PROMPT, "system",
                WorkflowState.KEY_AGENT_PROPERTIES, StagesUtil.agentProperties()));

        Map<String, Object> result = stage.execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "Sorry, I can't help with that.");
    }

    @Test
    void emitsStageStartedAndStageCompletedExactlyOnce() {
        when(generator.generate(any(WorkflowState.class), eq(StageId.GENERATE_REFUSAL), anyString()))
                .thenReturn("Sorry, I can't help with that.");

        WorkflowState state = StagesUtil.state(RoutingDecision.refuse("refusal", "request"));

        stage.execute(state);

        verify(streamer).stageStarted(state.runId(), StageId.GENERATE_REFUSAL);
        verify(streamer).stageCompleted(state.runId(), StageId.GENERATE_REFUSAL);
    }
}
