package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.execution.stage.StageSettings.StageSetting;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.agent.TriggerSource;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.workflowai.domain.workflow.StageId.CLASSIFICATION;
import static org.assertj.core.api.Assertions.assertThat;

class ClassificationStageSchedulingTest {

    private static final String EXECUTE_JSON = """
            {"decisionMode":"EXECUTE","detectedTopics":[],"extractedIntent":"daily standup summary",
            "clarificationQuestion":null,"reason":"clear recurring request","cronExpression":"0 9 * * *",
            "runOnceAt":"2026-08-04T10:00:00.000Z","scheduleInstruction":"Post the daily standup summary"}
            """;

    private static final String CLARIFY_JSON = """
            {"decisionMode":"CLARIFY","detectedTopics":[],"extractedIntent":"schedule something",
            "clarificationQuestion":"How often should this run?","reason":"missing frequency",
            "cronExpression":null,"runOnceAt":"2026-08-04T10:00:00.000Z","scheduleInstruction":null}
            """;

    private static final String REFUSE_JSON = """
            {"decisionMode":"REFUSE","detectedTopics":[],"extractedIntent":"nested schedule request",
            "clarificationQuestion":null,"reason":"tasks cannot schedule other tasks",
            "cronExpression":null,"runOnceAt":"2026-08-04T10:00:00.000Z","scheduleInstruction":null}
            """;

    @Test
    void missingFrequencyProducesClarify() {
        RecordingProvider provider = new RecordingProvider(CLARIFY_JSON);
        ClassificationStage stage = stage(provider);

        RoutingDecision decision = classify(stage, "remind me", true);

        assertThat(decision.decisionMode()).isEqualTo(DecisionMode.CLARIFY);
        assertThat(decision.clarificationQuestion()).isEqualTo("How often should this run?");
    }

    @Test
    void instructionThatIsItselfASchedulingRequestProducesRefuse() {
        RecordingProvider provider = new RecordingProvider(REFUSE_JSON);
        ClassificationStage stage = stage(provider);

        RoutingDecision decision = classify(stage, "/schedule every day, ping me", true);

        assertThat(decision.decisionMode()).isEqualTo(DecisionMode.REFUSE);
    }

    @Test
    void extractsCronAndInstructionOnSuccess() {
        RecordingProvider provider = new RecordingProvider(EXECUTE_JSON);
        ClassificationStage stage = stage(provider);

        RoutingDecision decision = classify(stage, "daily standup summary at 9am", true);

        assertThat(decision.decisionMode()).isEqualTo(DecisionMode.EXECUTE);
        assertThat(decision.cronExpression()).isEqualTo("0 9 * * *");
        assertThat(decision.scheduleInstruction()).isEqualTo("Post the daily standup summary");
    }

    @Test
    void schedulingRequestsAugmentThePromptWithExtractionInstructions() {
        RecordingProvider provider = new RecordingProvider(EXECUTE_JSON);
        ClassificationStage stage = stage(provider);

        classify(stage, "daily standup summary at 9am", true);

        assertThat(provider.callRequests).singleElement()
                .extracting(ChatCompletionRequest::message)
                .satisfies(message -> assertThat(message).contains("cronExpression").contains("REFUSE"));
    }

    @Test
    void nonSchedulingRequestsDoNotMentionScheduling() {
        RecordingProvider provider = new RecordingProvider(EXECUTE_JSON);
        ClassificationStage stage = stage(provider);

        classify(stage, "what's the weather", false);

        assertThat(provider.callRequests).singleElement()
                .extracting(ChatCompletionRequest::message)
                .satisfies(message -> assertThat(message).doesNotContain("cronExpression"));
    }

    private RoutingDecision classify(ClassificationStage stage, String userMessage, boolean schedulingRequested) {
        Map<String, Object> result = stage.execute(state(userMessage, schedulingRequested));
        return (RoutingDecision) result.get(WorkflowState.KEY_ROUTING_DECISION);
    }

    private ClassificationStage stage(ChatProvider provider) {
        StageSettings settings = new StageSettings(List.of(
                new StageSetting(CLASSIFICATION, ChatProviderId.Ollama, "classification-model", 0.1)));
        ChatProviderRegistry registry = new ChatProviderRegistry(List.of(provider));
        return new ClassificationStage(registry, settings, JsonMapper.builder().build(), List.of());
    }

    private WorkflowState state(String userMessage, boolean schedulingRequested) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, userMessage,
                WorkflowState.KEY_TRIGGER_SOURCE, TriggerSource.USER_MESSAGE,
                WorkflowState.KEY_SCHEDULING_REQUESTED, schedulingRequested,
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties()));
    }

    private AgentProperties agentProperties() {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                WorkflowId.STANDARD, ChatProviderId.Ollama, "agent-model", 0.9, "system", true,
                new WorkflowPolicy(List.of(), null, "fallback"));
    }

    private static final class RecordingProvider implements ChatProvider {
        private final String response;
        private final List<ChatCompletionRequest> callRequests = new ArrayList<>();

        private RecordingProvider(String response) {
            this.response = response;
        }

        @Override
        public ChatProviderId getId() {
            return ChatProviderId.Ollama;
        }

        @Override
        public String stream(ChatCompletionRequest request, java.util.function.Consumer<String> tokenConsumer) {
            throw new UnsupportedOperationException("classification does not stream");
        }

        @Override
        public String call(ChatCompletionRequest request) {
            callRequests.add(request);
            return response;
        }

        @Override
        public boolean supportsModel(String model) {
            return true;
        }

        @Override
        public Set<String> supportedModels() {
            return Set.of();
        }
    }
}