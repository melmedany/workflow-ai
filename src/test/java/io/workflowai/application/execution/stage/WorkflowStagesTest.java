package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowStagesTest {

    @Test
    void usesEachStageConfiguredProvider() throws InterruptedException {
        TestProviders providers = new TestProviders();
        RecordingConversationMessageStorage messages = new RecordingConversationMessageStorage();
        RecordingMemoryStorage memory = new RecordingMemoryStorage();
        StageSettings stagesProperties = stagesProperties();
        PersistResponseStage persistResponseStage = new PersistResponseStage(messages, List.of());
        DecisionResponseGenerator generator = new DecisionResponseGenerator(providers.registry(), stagesProperties);

        GenerateClarificationStage clarificationStage = new GenerateClarificationStage(
                providers.registry(), stagesProperties, persistResponseStage, List.of());
        WorkflowState clarification = state(new RoutingDecision(
                DecisionMode.CLARIFY, List.of(), "request", "", "clarify"));
        clarificationStage.execute(clarification);
        assertRequest(providers.provider(ChatProviderId.Bonzai).callRequests(), "clarification-model", 0.3);

        providers.clear();
        GenerateRedirectStage redirectStage = new GenerateRedirectStage(generator, persistResponseStage, List.of());
        WorkflowState redirect = state(RoutingDecision.redirect("redirect", "request"));
        redirectStage.execute(redirect);
        assertRequest(providers.provider(ChatProviderId.OpenAI).streamRequests(), "redirect-model", 0.4);

        providers.clear();
        GenerateGreetingStage greetingStage = new GenerateGreetingStage(generator, persistResponseStage, List.of());
        WorkflowState greeting = state(RoutingDecision.greet("greeting", "request"));
        greetingStage.execute(greeting);
        assertRequest(providers.provider(ChatProviderId.Anthropic).streamRequests(), "greeting-model", 0.5);

        providers.clear();
        GenerateRefusalStage refusalStage = new GenerateRefusalStage(generator, persistResponseStage, List.of());
        WorkflowState refusal = state(RoutingDecision.refuse("refusal", "request"));
        refusalStage.execute(refusal);
        assertRequest(providers.provider(ChatProviderId.Bonzai).streamRequests(), "refusal-model", 0.7);

        providers.clear();
        CompactMemoryStage compactMemoryStage = new CompactMemoryStage(providers.registry(), stagesProperties, memory);
        WorkflowState compactMemory = state(RoutingDecision.greet("greeting", "request"));
        compactMemoryStage.execute(compactMemory);
        waitFor(() -> !providers.provider(ChatProviderId.Ollama).callRequests().isEmpty());
        assertRequest(providers.provider(ChatProviderId.Ollama).callRequests(), "memory-model", 0.6);
    }

    @Test
    void buffersAndGuardsEachDecisionBranchBeforeDeliveringItOnce() {
        for (StageId stageId : List.of(StageId.GENERATE_REDIRECT, StageId.GENERATE_GREETING, StageId.GENERATE_REFUSAL)) {
            TestProviders providers = new TestProviders();
            RecordingConversationMessageStorage messages = new RecordingConversationMessageStorage();
            StageSettings stagesProperties = stagesProperties();
            PersistResponseStage persistResponseStage = new PersistResponseStage(messages, List.of());
            DecisionResponseGenerator generator = new DecisionResponseGenerator(providers.registry(), stagesProperties);
            WorkflowState state = state(decision(stageId));

            switch (stageId) {
                case GENERATE_REDIRECT -> new GenerateRedirectStage(generator, persistResponseStage, List.of()).execute(state);
                case GENERATE_GREETING -> new GenerateGreetingStage(generator, persistResponseStage, List.of()).execute(state);
                case GENERATE_REFUSAL -> new GenerateRefusalStage(generator, persistResponseStage, List.of()).execute(state);
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

    private void assertRequest(List<ChatCompletionRequest> requests, String model, double temperature) {
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.model()).isEqualTo(model);
            assertThat(request.temperature()).isEqualTo(temperature);
        });
    }

    private StageSettings stagesProperties() {
        return new StageSettings(List.of(
                stage(StageId.CLASSIFICATION, ChatProviderId.Ollama, "classification-model", 0.1),
                stage(StageId.GENERATE_CLARIFICATION, ChatProviderId.Bonzai, "clarification-model", 0.3),
                stage(StageId.GENERATE_REDIRECT, ChatProviderId.OpenAI, "redirect-model", 0.4),
                stage(StageId.GENERATE_GREETING, ChatProviderId.Anthropic, "greeting-model", 0.5),
                stage(StageId.GENERATE_REFUSAL, ChatProviderId.Bonzai, "refusal-model", 0.7),
                stage(StageId.COMPACT_MEMORY, ChatProviderId.Ollama, "memory-model", 0.6)));
    }

    private WorkflowState state(RoutingDecision decision) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "request",
                WorkflowState.KEY_SYSTEM_PROMPT, "system",
                WorkflowState.KEY_MEMORY_CONTEXT, "memory",
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties(),
                WorkflowState.KEY_ROUTING_DECISION, decision,
                WorkflowState.KEY_GENERATED_RESPONSE, "response"));
    }

    private AgentProperties agentProperties() {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                ChatProviderId.Ollama, "agent-model", 0.9, "system", true,
                new WorkflowPolicy(List.of(), null, "fallback"));
    }

    private StageSettings.StageSetting stage(StageId stageId, ChatProviderId providerId,
                                                    String model, double temperature) {
        return new StageSettings.StageSetting(stageId, providerId, model, temperature);
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
        private final Map<ChatProviderId, RecordingProvider> providers = new EnumMap<>(ChatProviderId.class);

        private TestProviders() {
            providers.put(ChatProviderId.Ollama, new RecordingProvider(ChatProviderId.Ollama, "Ollama response"));
            providers.put(ChatProviderId.OpenAI, new RecordingProvider(ChatProviderId.OpenAI, "OpenAI response"));
            providers.put(ChatProviderId.Anthropic, new RecordingProvider(ChatProviderId.Anthropic, "Anthropic response"));
            providers.put(ChatProviderId.Bonzai, new RecordingProvider(ChatProviderId.Bonzai, "Bonzai response"));
        }

        private ChatProviderRegistry registry() {
            return new ChatProviderRegistry(List.copyOf(providers.values()));
        }

        private RecordingProvider provider(ChatProviderId providerId) {
            return providers.get(providerId);
        }

        private void clear() {
            providers.values().forEach(RecordingProvider::clear);
        }
    }

    private static final class RecordingProvider implements ChatProvider {
        private final ChatProviderId id;
        private final String response;
        private final List<ChatCompletionRequest> streamRequests = new ArrayList<>();
        private final List<ChatCompletionRequest> callRequests = new ArrayList<>();

        private RecordingProvider(ChatProviderId id, String response) {
            this.id = id;
            this.response = response;
        }

        @Override
        public ChatProviderId getId() {
            return id;
        }

        @Override
        public String stream(ChatCompletionRequest request, Consumer<String> tokenConsumer) {
            streamRequests.add(request);
            for (String token : response.split("(?<=\\s)")) {
                tokenConsumer.accept(token);
            }
            return response;
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
        public java.util.Set<String> supportedModels() {
            return java.util.Set.of();
        }

        private List<ChatCompletionRequest> streamRequests() {
            return streamRequests;
        }

        private List<ChatCompletionRequest> callRequests() {
            return callRequests;
        }

        private void clear() {
            streamRequests.clear();
            callRequests.clear();
        }
    }

    private record RecordingConversationMessageStorage(List<ConversationMessage> messages) implements ConversationMessageStorage {
        private RecordingConversationMessageStorage() {
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