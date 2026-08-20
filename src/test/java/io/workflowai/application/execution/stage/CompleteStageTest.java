package io.workflowai.application.execution.stage;

import io.workflowai.application.port.out.NotificationChannel;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CompleteStageTest {

    private final WorkflowEventStreamer streamer = mock();
    private final NotificationChannel channel = mock();

    @Test
    void emitsConversationCompletedForEveryStreamer() {
        WorkflowState state = StagesUtil.state("final answer");

        new CompleteStage(List.of(streamer), List.of()).execute(state);

        verify(streamer).conversationCompleted(state.runId());
    }

    @Test
    void notifiesEveryRegisteredChannelWithTheFinalAgentMessage() {
        WorkflowState state = StagesUtil.state("final answer");

        new CompleteStage(List.of(streamer), List.of(channel)).execute(state);

        verify(channel).notify(eq(state.agentProperties().id()), eq(state.conversationId()),
                argThat(message -> message.content().equals("final answer")));
    }

    @Test
    void notifiedMessageContentIsEmptyStringWhenNoResponseWasGenerated() {
        WorkflowState state = new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_AGENT_PROPERTIES, StagesUtil.agentProperties()));

        new CompleteStage(List.of(streamer), List.of(channel)).execute(state);

        verify(channel).notify(any(), any(), argThat(message -> message.content().isEmpty()));
    }

    @Test
    void completesNormallyWithNoRegisteredNotificationChannels() {
        WorkflowState state = StagesUtil.state("final answer");

        Map<String, Object> result = new CompleteStage(List.of(streamer), List.of()).execute(state);

        assertThat(result).isEmpty();
        verifyNoInteractions(channel);
    }
}