package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static io.workflowai.domain.workflow.DecisionMode.EXECUTE;
import static io.workflowai.domain.workflow.DecisionMode.REFUSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ClassificationStageTest {

    private static final String EXECUTE_JSON = """
            {
              "decisionMode": "EXECUTE",
              "detectedTopics": [],
              "extractedIntent": "answer a question",
              "clarificationQuestion": null,
              "reason": "clear in-scope request",
              "scheduleType": null,
              "startDateTime": null,
              "duration": null,
              "scheduleInstruction": null
            }
            """;

    private final ChatProvider provider = mock();
    private final WorkflowEventStreamer streamer = mock();

    @BeforeEach
    void setUp() {
        when(provider.getId()).thenReturn(Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);
    }

    @Test
    void successfulClassificationReturnsRoutingDecisionAndEmitsEventsInOrder() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn(EXECUTE_JSON);

        WorkflowState state = StagesUtil.state("what's the weather", false);

        Map<String, Object> result = stage().execute(state);

        RoutingDecision decision = (RoutingDecision) result.get(WorkflowState.KEY_ROUTING_DECISION);
        assertThat(decision.decisionMode()).isEqualTo(EXECUTE);
        assertThat(decision.extractedIntent()).isEqualTo("answer a question");

        verify(streamer).stageStarted(state.runId(), StageId.CLASSIFICATION);
        verify(streamer).stageCompleted(state.runId(), StageId.CLASSIFICATION);
        verify(streamer).decisionMade(state.runId(), decision);
        verifyNoMoreInteractions(streamer);
    }

    @Test
    void providerFailureFallsBackToRefuseWithoutThrowing() {
        when(provider.call(any(ChatCompletionRequest.class))).thenThrow(new RuntimeException("provider unreachable"));

        WorkflowState state = StagesUtil.state("what's the weather", false);

        Map<String, Object> result = stage().execute(state);

        RoutingDecision decision = (RoutingDecision) result.get(WorkflowState.KEY_ROUTING_DECISION);
        assertThat(decision.decisionMode()).isEqualTo(REFUSE);
        assertThat(decision.reason()).contains("Classification unavailable: provider unreachable");
        assertThat(decision.extractedIntent()).isEqualTo("what's the weather");

        verify(streamer).stageStarted(state.runId(), StageId.CLASSIFICATION);
        verify(streamer).stageCompleted(state.runId(), StageId.CLASSIFICATION);
        verify(streamer).decisionMade(state.runId(), decision);
    }

    @Test
    void malformedClassificationResponseRefuseWithoutThrowing() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn("not valid json");

        WorkflowState state = StagesUtil.state("what's the weather", false);

        Map<String, Object> result = stage().execute(state);

        RoutingDecision decision = (RoutingDecision) result.get(WorkflowState.KEY_ROUTING_DECISION);
        assertThat(decision.decisionMode()).isEqualTo(REFUSE);
        assertThat(decision.reason()).contains("Classification unavailable: Unrecognized token");
        assertThat(decision.extractedIntent()).isEqualTo("what's the weather");

        verify(streamer).stageStarted(state.runId(), StageId.CLASSIFICATION);
        verify(streamer).stageCompleted(state.runId(), StageId.CLASSIFICATION);
        verify(streamer).decisionMade(state.runId(), decision);
    }

    private ClassificationStage stage() {
        StageSettings settings = new StageSettings(List.of(
                new StageSettings.StageSetting(StageId.CLASSIFICATION, Ollama, "ollama-model", 0.1)));

        return new ClassificationStage(new ChatProviderRegistry(List.of(provider)), settings,
                JsonMapper.builder().build(), List.of(streamer));
    }
}
