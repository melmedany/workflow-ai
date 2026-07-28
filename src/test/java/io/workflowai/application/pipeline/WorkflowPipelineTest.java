package io.workflowai.application.pipeline;

import io.workflowai.application.GuardrailProperties;
import io.workflowai.application.LlmProviderId;
import io.workflowai.application.LlmProviderRegistry;
import io.workflowai.application.StagesProperties;
import io.workflowai.domain.model.AgentProperties;
import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.domain.model.LlmRequest;
import io.workflowai.domain.model.RoutingDecision;
import io.workflowai.domain.workflow.GuardrailChecker;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.ports.outbound.AgentMemoryStorage;
import io.workflowai.ports.outbound.LlmProvider;
import io.workflowai.ports.outbound.MessageStorage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowPipelineTest {

    @Test
    void usesEachStageConfiguredProvider() throws InterruptedException {
        TestProviders providers = new TestProviders();
        RecordingMessageStorage messages = new RecordingMessageStorage();
        RecordingMemoryStorage memory = new RecordingMemoryStorage();
        WorkflowPipeline pipeline = pipeline(providers, messages, memory);

        WorkflowContext clarification = context(new RoutingDecision(
                io.workflowai.domain.workflow.DecisionMode.CLARIFY, List.of(), "request", "", "clarify"));
        pipeline.generateClarification(clarification);
        assertRequest(providers.provider(LlmProviderId.Bonzai).callRequests(), "clarification-model", 0.3);

        providers.clear();
        WorkflowContext redirect = context(RoutingDecision.redirect("redirect", "request"));
        pipeline.generateRedirect(redirect);
        assertRequest(providers.provider(LlmProviderId.OpenAI).streamRequests(), "redirect-model", 0.4);

        providers.clear();
        WorkflowContext greeting = context(RoutingDecision.greet("greeting", "request"));
        pipeline.generateGreeting(greeting);
        assertRequest(providers.provider(LlmProviderId.Anthropic).streamRequests(), "greeting-model", 0.5);

        providers.clear();
        WorkflowContext refusal = context(RoutingDecision.refuse("refusal", "request"));
        pipeline.generateRefusal(refusal);
        assertRequest(providers.provider(LlmProviderId.Bonzai).streamRequests(), "refusal-model", 0.7);

        providers.clear();
        WorkflowContext compactMemory = context(RoutingDecision.greet("greeting", "request"));
        pipeline.compactMemory(compactMemory);
        waitFor(() -> !providers.provider(LlmProviderId.Ollama).callRequests().isEmpty());
        assertRequest(providers.provider(LlmProviderId.Ollama).callRequests(), "memory-model", 0.6);
    }

    @Test
    void buffersAndGuardsEachDecisionBranchBeforeDeliveringItOnce() {
        for (StageId stageId : List.of(StageId.GENERATE_REDIRECT, StageId.GENERATE_GREETING, StageId.GENERATE_REFUSAL)) {
            TestProviders providers = new TestProviders();
            RecordingMessageStorage messages = new RecordingMessageStorage();
            WorkflowPipeline pipeline = pipeline(providers, messages, new RecordingMemoryStorage());
            WorkflowContext state = context(decision(stageId));

            switch (stageId) {
                case GENERATE_REDIRECT -> pipeline.generateRedirect(state);
                case GENERATE_GREETING -> pipeline.generateGreeting(state);
                case GENERATE_REFUSAL -> pipeline.generateRefusal(state);
                default -> throw new IllegalArgumentException("Unexpected decision stage: " + stageId);
            }

            String expected = switch (stageId) {
                case GENERATE_REDIRECT -> "OpenAI response";
                case GENERATE_GREETING -> "Anthropic response";
                case GENERATE_REFUSAL -> "Bonzai response";
                default -> throw new IllegalArgumentException("Unexpected decision stage: " + stageId);
            };

            assertThat(messages.messages()).singleElement().extracting(ConversationMessage::content).isEqualTo(expected);
        }
    }

    private void assertRequest(List<LlmRequest> requests, String model, double temperature) {
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.model()).isEqualTo(model);
            assertThat(request.temperature()).isEqualTo(temperature);
        });
    }

    private WorkflowPipeline pipeline(TestProviders providers, RecordingMessageStorage messages,
                                      RecordingMemoryStorage memory) {
        return new WorkflowPipeline(
                agentProperties(),
                new LlmProviderRegistry(providers.all()),
                new StagesProperties(List.of(
                        stage(StageId.CLASSIFICATION, LlmProviderId.Ollama, "classification-model", 0.1),
                        stage(StageId.GENERATE_CLARIFICATION, LlmProviderId.Bonzai, "clarification-model", 0.3),
                        stage(StageId.GENERATE_REDIRECT, LlmProviderId.OpenAI, "redirect-model", 0.4),
                        stage(StageId.GENERATE_GREETING, LlmProviderId.Anthropic, "greeting-model", 0.5),
                        stage(StageId.GENERATE_REFUSAL, LlmProviderId.Bonzai, "refusal-model", 0.7),
                        stage(StageId.COMPACT_MEMORY, LlmProviderId.Ollama, "memory-model", 0.6))),
                messages,
                memory,
                List.of(),
                List.of(),
                new GuardrailChecker(new GuardrailProperties(List.of(), List.of())),
                JsonMapper.builder().build());
    }

    private WorkflowContext context(RoutingDecision decision) {
        return new WorkflowContext(Map.of(
                WorkflowContext.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowContext.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowContext.KEY_USER_MESSAGE, "request",
                WorkflowContext.KEY_SYSTEM_PROMPT, "system",
                WorkflowContext.KEY_MEMORY_CONTEXT, "memory",
                WorkflowContext.KEY_ROUTING_DECISION, decision,
                WorkflowContext.KEY_GENERATED_RESPONSE, "response"));
    }

    private AgentProperties agentProperties() {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                LlmProviderId.Ollama, "agent-model", 0.9, "system", true,
                new WorkflowPolicy(List.of(), null, "fallback"));
    }

    private StagesProperties.StageProperties stage(StageId stageId, LlmProviderId providerId,
                                                    String model, double temperature) {
        return new StagesProperties.StageProperties(stageId, providerId, model, temperature);
    }

    private RoutingDecision decision(StageId stageId) {
        return switch (stageId) {
            case GENERATE_REDIRECT -> RoutingDecision.redirect("redirect", "request");
            case GENERATE_GREETING -> RoutingDecision.greet("greeting", "request");
            case GENERATE_REFUSAL -> RoutingDecision.refuse("refusal", "request");
            default -> throw new IllegalArgumentException("Unexpected decision stage: " + stageId);
        };
    }

    private void waitFor(Condition condition) throws InterruptedException {
        for (int attempts = 0; attempts < 100; attempts++) {
            if (condition.matches()) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for asynchronous memory compaction");
    }

    private interface Condition {
        boolean matches();
    }

    private static final class TestProviders {
        private final Map<LlmProviderId, RecordingProvider> providers = new EnumMap<>(LlmProviderId.class);

        private TestProviders() {
            providers.put(LlmProviderId.Ollama, new RecordingProvider(LlmProviderId.Ollama, "Ollama response"));
            providers.put(LlmProviderId.OpenAI, new RecordingProvider(LlmProviderId.OpenAI, "OpenAI response"));
            providers.put(LlmProviderId.Anthropic, new RecordingProvider(LlmProviderId.Anthropic, "Anthropic response"));
            providers.put(LlmProviderId.Bonzai, new RecordingProvider(LlmProviderId.Bonzai, "Bonzai response"));
        }

        private List<LlmProvider> all() {
            return List.copyOf(providers.values());
        }

        private RecordingProvider provider(LlmProviderId providerId) {
            return providers.get(providerId);
        }

        private void clear() {
            providers.values().forEach(RecordingProvider::clear);
        }
    }

    private static final class RecordingProvider implements LlmProvider {
        private final LlmProviderId id;
        private final String response;
        private final List<LlmRequest> streamRequests = new ArrayList<>();
        private final List<LlmRequest> callRequests = new ArrayList<>();

        private RecordingProvider(LlmProviderId id, String response) {
            this.id = id;
            this.response = response;
        }

        @Override
        public LlmProviderId getId() {
            return id;
        }

        @Override
        public String stream(LlmRequest request, Consumer<String> tokenConsumer) {
            streamRequests.add(request);
            for (String token : response.split("(?<=\\s)")) {
                tokenConsumer.accept(token);
            }
            return response;
        }

        @Override
        public String call(LlmRequest request) {
            callRequests.add(request);
            return response;
        }

        @Override
        public boolean supportsModel(String model) {
            return true;
        }

        @Override
        public java.util.Set<String> supportedModels() {
            return java.util.Set.of();
        }

        private List<LlmRequest> streamRequests() {
            return streamRequests;
        }

        private List<LlmRequest> callRequests() {
            return callRequests;
        }

        private void clear() {
            streamRequests.clear();
            callRequests.clear();
        }
    }

    private record RecordingMessageStorage(List<ConversationMessage> messages) implements MessageStorage {
        private RecordingMessageStorage() {
            this(new ArrayList<>());
        }

        @Override
        public void save(UUID conversationId, UUID agentId, ConversationMessage message) {
            messages.add(message);
        }

        @Override
        public List<ConversationMessage> findByAgentIdAndConversationId(UUID agentId, UUID conversationId) {
            return List.copyOf(messages);
        }

        private void clear() {
            messages.clear();
        }
    }

    private record RecordingMemoryStorage(List<String> replacements) implements AgentMemoryStorage {
        private RecordingMemoryStorage() {
            this(new ArrayList<>());
        }

        @Override
        public Optional<String> getMemory(UUID conversationId, UUID agentId) {
            return Optional.empty();
        }

        @Override
        public void replace(UUID conversationId, UUID agentId, String content) {
            replacements.add(content);
        }

        @Override
        public void clear(UUID conversationId) {
        }
    }
}
