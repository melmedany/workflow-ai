package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.workflowai.application.execution.stage.StageSettings.StageSetting;
import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateClarificationStageTest {

    private final ChatProvider provider = mock();
    private final ConversationMessageStorage messages = mock();
    private final WorkflowEventStreamer streamer = mock();

    @BeforeEach
    void setUp() {
        when(provider.getId()).thenReturn(Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);
    }

    @Test
    void usesTheClassifierProvidedQuestionWithoutCallingTheProvider() {
        WorkflowState state = StagesUtil.state(RoutingDecision.clarify(
                "missing frequency", "schedule something", "How often should this run?"));

        Map<String, Object> result = stage().execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "How often should this run?");
        verify(provider, never()).call(any());
        verify(messages).save(any(), any(),
                argThat(message -> message.content().equals("How often should this run?")));
    }

    @Test
    void generatesAQuestionWhenClassifierQuestionIsBlank() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn("What would you like me to do?");

        WorkflowState state = StagesUtil.state(RoutingDecision.clarify("missing detail", "do something", "   "));

        Map<String, Object> result = stage().execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "What would you like me to do?");
    }

    @Test
    void generatesAQuestionWhenNoRoutingDecisionIsPresent() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn("Could you clarify your request?");

        WorkflowState state = new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "help",
                WorkflowState.KEY_SYSTEM_PROMPT, "system",
                WorkflowState.KEY_MEMORY_CONTEXT, "",
                WorkflowState.KEY_AGENT_PROPERTIES, StagesUtil.agentProperties()));

        Map<String, Object> result = stage().execute(state);

        assertThat(result).containsEntry(WorkflowState.KEY_GENERATED_RESPONSE, "Could you clarify your request?");
    }

    @Test
    void emitsStageStartedAndStageCompletedExactlyOnce() {
        WorkflowState state = StagesUtil.state(RoutingDecision.clarify(
                "missing frequency", "schedule something", "How often should this run?"));

        stage().execute(state);

        verify(streamer).stageStarted(state.runId(), StageId.GENERATE_CLARIFICATION);
        verify(streamer).stageCompleted(state.runId(), StageId.GENERATE_CLARIFICATION);
    }

    private GenerateClarificationStage stage() {
        StageSettings settings = new StageSettings(List.of(
                new StageSetting(StageId.GENERATE_CLARIFICATION, Ollama, "clarification-model", 0.3)));

        return new GenerateClarificationStage(
                new ChatProviderRegistry(List.of(provider)),
                settings,
                new PersistResponseStage(messages, List.of()),
                List.of(streamer));
    }
}
