package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static io.workflowai.application.execution.stage.StageSettings.StageSetting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowStagesTest {

    private final ChatProviderRegistry providers = mock();
    private final ChatProvider ollama = mock();
    private final ChatProvider openAi = mock();
    private final ChatProvider anthropic = mock();
    private final ChatProvider bonzai = mock();
    private final ChatProvider grok = mock();

    private final ConversationMessageStorage messages = mock();
    private final AgentMemoryStorage memory = mock();

    private final StageSettings stageSettings = stageSettings();

    private final PersistResponseStage persistResponseStage =
            new PersistResponseStage(messages, List.of());

    private final DecisionResponseGenerator generator = new DecisionResponseGenerator(providers, stageSettings);

    @BeforeEach
    void setUp() {
        when(providers.get(ChatProviderId.Ollama)).thenReturn(ollama);
        when(providers.get(ChatProviderId.OpenAI)).thenReturn(openAi);
        when(providers.get(ChatProviderId.Anthropic)).thenReturn(anthropic);
        when(providers.get(ChatProviderId.Bonzai)).thenReturn(bonzai);
        when(providers.get(ChatProviderId.Grok)).thenReturn(grok);

        when(ollama.call(any(ChatCompletionRequest.class))).thenReturn("Ollama response");
        when(openAi.call(any(ChatCompletionRequest.class))).thenReturn("OpenAI response");
        when(anthropic.call(any(ChatCompletionRequest.class))).thenReturn("Anthropic response");
        when(bonzai.call(any(ChatCompletionRequest.class))).thenReturn("Bonzai response");

        when(ollama.stream(any(ChatCompletionRequest.class), any(Consumer.class))).thenAnswer(invocation -> {
            ChatCompletionRequest request = invocation.getArgument(0);
            Consumer<String> consumer = invocation.getArgument(1);

            consumer.accept("Ollama response");
            return "Ollama response";
        });

        when(openAi.stream(any(ChatCompletionRequest.class), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("OpenAI response");
            return "OpenAI response";
        });

        when(anthropic.stream(any(ChatCompletionRequest.class), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("Anthropic response");
            return "Anthropic response";
        });

        when(bonzai.stream(any(ChatCompletionRequest.class), any(Consumer.class))).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("Bonzai response");
            return "Bonzai response";
        });
    }

    @Test
    void usesConfiguredProviderForClarification() {
        var stage = new GenerateClarificationStage(
                providers,
                stageSettings,
                persistResponseStage,
                List.of());

        stage.execute(StagesUtil.state(new RoutingDecision(
                DecisionMode.CLARIFY,
                List.of(),
                "request",
                "",
                "clarify")));

        var request = captureRequest(bonzai, "clarification-model", 0.3);

        assertThat(request.model()).isEqualTo("clarification-model");
        assertThat(request.temperature()).isEqualTo(0.3);
    }

    @Test
    void usesConfiguredProviderForRedirect() {
        var stage = new GenerateRedirectStage(
                generator,
                persistResponseStage,
                List.of());

        stage.execute(StagesUtil.state(RoutingDecision.redirect(
                "redirect",
                "request")));

        assertRequest(openAi, "redirect-model", 0.4);
    }

    @Test
    void usesConfiguredProviderForGreeting() {
        var stage = new GenerateGreetingStage(
                generator,
                persistResponseStage,
                List.of());

        stage.execute(StagesUtil.state(RoutingDecision.greet(
                "greeting",
                "request")));

        assertRequest(anthropic, "greeting-model", 0.5);
    }

    @Test
    void usesConfiguredProviderForRefusal() {
        var stage = new GenerateRefusalStage(
                generator,
                persistResponseStage,
                List.of());

        stage.execute(StagesUtil.state(RoutingDecision.refuse(
                "refusal",
                "request")));

        assertRequest(bonzai, "refusal-model", 0.7);
    }

    @Test
    void usesConfiguredProviderForMemoryCompaction() {
        var stage = new CompactMemoryStage(
                providers,
                stageSettings,
                memory,
                List.of());

        stage.execute(StagesUtil.state(RoutingDecision.greet(
                "greeting",
                "request")));

        verify(ollama).call(argThat(request ->
                request.model().equals("memory-model")
                        && request.temperature() == 0.6));
    }

    @Test
    void persistsRedirectResponseOnce() {
        executeDecisionStage(StageId.GENERATE_REDIRECT);

        verify(messages).save(any(UUID.class), any(UUID.class),
                argThat(message ->
                        message.content().equals("OpenAI response")));
    }

    @Test
    void persistsGreetingResponseOnce() {
        executeDecisionStage(StageId.GENERATE_GREETING);

        verify(messages).save(any(UUID.class), any(UUID.class),
                argThat(message ->
                        message.content().equals("Anthropic response")));
    }

    @Test
    void persistsRefusalResponseOnce() {
        executeDecisionStage(StageId.GENERATE_REFUSAL);

        verify(messages).save(any(UUID.class), any(UUID.class),
                argThat(message ->
                        message.content().equals("Bonzai response")));
    }

    private void executeDecisionStage(StageId stageId) {
        var state = StagesUtil.state(StagesUtil.decision(stageId));

        switch (stageId) {
            case GENERATE_REDIRECT -> new GenerateRedirectStage(
                    generator,
                    persistResponseStage,
                    List.of()).execute(state);

            case GENERATE_GREETING -> new GenerateGreetingStage(
                    generator,
                    persistResponseStage,
                    List.of()).execute(state);

            case GENERATE_REFUSAL -> new GenerateRefusalStage(
                    generator,
                    persistResponseStage,
                    List.of()).execute(state);

            default -> throw new IllegalArgumentException(
                    "Unexpected decision stage: " + stageId);
        }
    }

    private ChatCompletionRequest captureRequest(ChatProvider provider, String model, double temperature) {
        ArgumentCaptor<ChatCompletionRequest> captor = ArgumentCaptor.forClass(ChatCompletionRequest.class);

        verify(provider).call(captor.capture());

        assertThat(captor.getValue().model()).isEqualTo(model);
        assertThat(captor.getValue().temperature()).isEqualTo(temperature);

        return captor.getValue();
    }

    private StageSettings stageSettings() {
        return new StageSettings(List.of(
                stageSetting(StageId.CLASSIFICATION, ChatProviderId.Ollama, "classification-model", 0.1),
                stageSetting(StageId.GENERATE_CLARIFICATION, ChatProviderId.Bonzai, "clarification-model", 0.3),
                stageSetting(StageId.GENERATE_REDIRECT, ChatProviderId.OpenAI, "redirect-model", 0.4),
                stageSetting(StageId.SELF_VERIFICATION, ChatProviderId.Grok, "verification-model", 0.4),
                stageSetting(StageId.GENERATE_GREETING, ChatProviderId.Anthropic, "greeting-model", 0.5),
                stageSetting(StageId.GENERATE_REFUSAL, ChatProviderId.Bonzai, "refusal-model", 0.7),
                stageSetting(StageId.COMPACT_MEMORY, ChatProviderId.Ollama, "memory-model", 0.6)));
    }

    private StageSetting stageSetting(StageId stageId, ChatProviderId providerId,
                                                    String model, double temperature) {
        return new StageSetting(stageId, providerId, model, temperature);
    }

    private void assertRequest(ChatProvider provider, String model, double temperature) {
        verify(provider).stream(
                argThat(request ->
                        request.model().equals(model)
                                && request.temperature() == temperature),
                any(Consumer.class));
    }
}