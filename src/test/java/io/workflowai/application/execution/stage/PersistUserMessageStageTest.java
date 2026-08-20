package io.workflowai.application.execution.stage;

import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.workflowai.domain.agent.TriggerSource.SYSTEM_TRIGGER;
import static io.workflowai.domain.agent.TriggerSource.USER_MESSAGE;
import static io.workflowai.domain.conversation.ConversationMessageRole.SYSTEM;
import static io.workflowai.domain.conversation.ConversationMessageRole.USER;
import static io.workflowai.domain.workflow.StageId.PERSIST_USER_MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class PersistUserMessageStageTest {

    private final ConversationMessageStorage storage = mock();
    private final WorkflowEventStreamer streamer = mock();

    private final PersistUserMessageStage stage =
            new PersistUserMessageStage(storage, List.of(streamer));

    @Test
    void userTriggeredMessageIsPersistedWithUserRole() {
        WorkflowState state = StagesUtil.state(USER_MESSAGE, "what's the weather");

        Map<String, Object> result = stage.execute(state);

        verify(storage).save(eq(state.conversationId()), eq(state.agentProperties().id()),
                argThat(message -> message.role() == USER
                        && message.content().equals("what's the weather")));

        verify(streamer).stageStarted(state.runId(), PERSIST_USER_MESSAGE);
        verify(streamer).stageCompleted(state.runId(), PERSIST_USER_MESSAGE);
        verifyNoMoreInteractions(streamer);

        assertThat(result).isEmpty();
    }

    @Test
    void systemTriggeredMessageIsPersistedWithSystemRole() {
        WorkflowState state = StagesUtil.state(SYSTEM_TRIGGER, "run the scheduled task");

        stage.execute(state);

        verify(storage).save(eq(state.conversationId()), eq(state.agentProperties().id()),
                argThat(message -> message.role() == SYSTEM
                        && message.content().equals("run the scheduled task")));
    }
}
