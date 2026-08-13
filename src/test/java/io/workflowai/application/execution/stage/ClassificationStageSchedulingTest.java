package io.workflowai.application.execution.stage;

import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType.RECURRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassificationStageSchedulingTest {

    private static final String EXECUTE_JSON = """
            {
              "decisionMode": "EXECUTE",
              "detectedTopics": [],
              "extractedIntent": "daily standup summary",
              "clarificationQuestion": null,
              "reason": "clear recurring request",
              "duration": "P1D",
              "scheduleType": "RECURRING",
              "scheduleInstruction": "Post the daily standup summary"
            }
            """;

    private static final String CLARIFY_JSON = """
            {
              "decisionMode": "CLARIFY",
              "detectedTopics": [],
              "extractedIntent": "schedule something",
              "clarificationQuestion": "How often should this run?",
              "reason": "missing frequency",
              "scheduleType": null,
              "duration": null,
              "scheduleInstruction": null
            }
            """;

    private static final String REFUSE_JSON = """
            {
              "decisionMode": "REFUSE",
              "detectedTopics": [],
              "extractedIntent": "nested schedule request",
              "clarificationQuestion": null,
              "reason": "tasks cannot schedule other tasks",
              "scheduleType": null,
              "duration": null,
              "scheduleInstruction": null
            }
            """;

    private final ChatProvider provider = mock();

    @BeforeEach
    void setUp() {
        when(provider.getId()).thenReturn(Ollama);
        when(provider.supportsModel(anyString())).thenReturn(true);
    }

    @Test
    void missingFrequencyProducesClarify() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn(CLARIFY_JSON);

        RoutingDecision decision = classify("remind me", true);

        assertThat(decision.decisionMode()).isEqualTo(DecisionMode.CLARIFY);
        assertThat(decision.clarificationQuestion()).isEqualTo("How often should this run?");
    }

    @Test
    void instructionThatIsItselfASchedulingRequestProducesRefuse() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn(REFUSE_JSON);

        RoutingDecision decision = classify("every day, ping me", true);

        assertThat(decision.decisionMode()).isEqualTo(DecisionMode.REFUSE);
    }

    @Test
    void extractsDurationAndInstructionOnSuccess() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn(EXECUTE_JSON);

        RoutingDecision decision =
                classify("daily standup summary at 9am", true);

        assertThat(decision)
                .extracting(
                        RoutingDecision::decisionMode,
                        RoutingDecision::scheduleType,
                        RoutingDecision::duration,
                        RoutingDecision::scheduleInstruction)
                .containsExactly(
                        DecisionMode.EXECUTE,
                        RECURRING.name(),
                        "P1D",
                        "Post the daily standup summary");
    }

    @Test
    void nonSchedulingRequestsDoNotMentionScheduling() {
        when(provider.call(any(ChatCompletionRequest.class))).thenReturn(EXECUTE_JSON);

        classify("what's the weather", false);

        var request = captureRequest();

        assertThat(request.message()).doesNotContain("duration");
    }

    private ChatCompletionRequest captureRequest() {
        var captor = ArgumentCaptor.forClass(
                ChatCompletionRequest.class);

        verify(provider).call(captor.capture());

        return captor.getValue();
    }

    private RoutingDecision classify(
            String userMessage,
            boolean schedulingRequested) {

        Map<String, Object> result = StagesUtil.classificationStage(provider)
                .execute(StagesUtil.state(userMessage, schedulingRequested));

        return (RoutingDecision) result.get(WorkflowState.KEY_ROUTING_DECISION);
    }
}