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

class GenerateRedirectStageTest {

    private final DecisionResponseGenerator generator = mock();
    private final ConversationMessageStorage messages = mock();
    private final WorkflowEventStreamer streamer = mock();

    private final GenerateRedirectStage stage = new GenerateRedirectStage(
            generator, new PersistResponseStage(messages, List.of()), List.of(streamer));

    @Test
    void generatesAndPersistsTheRedirect() {
        when(generator.generate(any(WorkflowState.class), eq(StageId.GENERATE_REDIRECT), anyString()))
                .thenReturn("I can help with the scheduling part of that.");

        WorkflowState state = StagesUtil.state(RoutingDecision.redirect("redirect", "request"));

        Map<String, Object> result = stage.execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "I can help with the scheduling part of that.");
        verify(messages).save(any(UUID.class), any(UUID.class),
                argThat(message -> message.content().equals("I can help with the scheduling part of that.")));
    }

    @Test
    void synthesizesADefaultRedirectDecisionWhenNoneIsPresent() {
        when(generator.generate(any(WorkflowState.class), eq(StageId.GENERATE_REDIRECT), anyString()))
                .thenReturn("Let me redirect you.");

        WorkflowState state = new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "mixed request",
                WorkflowState.KEY_SYSTEM_PROMPT, "system",
                WorkflowState.KEY_AGENT_PROPERTIES, StagesUtil.agentProperties()));

        Map<String, Object> result = stage.execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "Let me redirect you.");
    }

    @Test
    void emitsStageStartedAndStageCompletedExactlyOnce() {
        when(generator.generate(any(WorkflowState.class), eq(StageId.GENERATE_REDIRECT), anyString()))
                .thenReturn("Let me redirect you.");

        WorkflowState state = StagesUtil.state(RoutingDecision.redirect("redirect", "request"));

        stage.execute(state);

        verify(streamer).stageStarted(state.runId(), StageId.GENERATE_REDIRECT);
        verify(streamer).stageCompleted(state.runId(), StageId.GENERATE_REDIRECT);
    }
}
