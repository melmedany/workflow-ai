package io.workflowai.integration;

import io.workflowai.application.execution.AgentRequest;
import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.application.port.in.ConversationUseCase;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.task.ConversationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Sql
class ScheduleTaskTest extends IntegrationBase {

    // agent is defined via ScheduleTaskTest.sql
    private static final UUID AGENT_ID = UUID.fromString("29014fc2-5616-4ea2-8d15-0fe8c42afea5");
    private static final String SCHEDULE_MESSAGE = "/schedule every day at 9am, summarize open PRs";
    private static final String CLASSIFICATION_JSON = """
            {"decisionMode":"EXECUTE","detectedTopics":[],"extractedIntent":"schedule a daily PR summary",
            "clarificationQuestion":null,"reason":"clear recurring request","cronExpression":"0 9 * * *",
            "scheduleInstruction":"Summarize open PRs"}
            """;

    @Autowired
    private AgentUseCase agentUseCase;

    @Autowired
    private ConversationUseCase conversationUseCase;

    @Autowired
    private ConversationTaskStorage conversationTaskStorage;

    @MockitoBean
    private ChatProviderRegistry chatProviderRegistry;

    @BeforeEach
    void mockClassification() {
        ChatProvider ollama = new ChatProvider() {
            @Override
            public ChatProviderId getId() {
                return ChatProviderId.Ollama;
            }

            @Override
            public String stream(ChatCompletionRequest request, Consumer<String> tokenConsumer) {
                String response = "Test response";
                tokenConsumer.accept(response);
                return response;
            }

            @Override
            public String call(ChatCompletionRequest request) {
                return CLASSIFICATION_JSON;
            }

            @Override
            public boolean supportsModel(String model) {
                return true;
            }

            @Override
            public Set<String> supportedModels() {
                return Set.of();
            }
        };
        when(chatProviderRegistry.get(any())).thenReturn(ollama);
        when(chatProviderRegistry.supportedChatProviders()).thenReturn(Map.of(ChatProviderId.Ollama, Set.of()));
    }

    @Test
    void scheduleMessage_createsExactlyOneTask_andUpsertsOnRepeat() {
        UUID conversationId = conversationUseCase.createConversation(AGENT_ID, "hi").id();

        agentUseCase.trigger(AgentRequest.userMessage(AGENT_ID, conversationId, SCHEDULE_MESSAGE), _ -> {
        });

        List<ConversationTask> afterFirst = conversationTaskStorage.findByConversation(AGENT_ID, conversationId);
        assertThat(afterFirst).hasSize(1);
        ConversationTask task = afterFirst.getFirst();
        assertThat(task.schedule().cronExpression()).isEqualTo("0 9 * * *");
        assertThat(task.definition().instruction()).isEqualTo("Summarize open PRs");

        agentUseCase.trigger(AgentRequest.userMessage(AGENT_ID, conversationId, SCHEDULE_MESSAGE), _ -> {
        });

        List<ConversationTask> afterSecond = conversationTaskStorage.findByConversation(AGENT_ID, conversationId);
        assertThat(afterSecond).hasSize(1);
        assertThat(afterSecond.getFirst().id()).isEqualTo(task.id());
    }

    @Test
    void systemTriggeredMessage_neverCreatesTask_evenWithScheduleText() {
        UUID conversationId = conversationUseCase.createConversation(AGENT_ID, "hi").id();
        UUID taskId = UUID.randomUUID();

        agentUseCase.trigger(AgentRequest.systemTrigger(AGENT_ID, conversationId, taskId, SCHEDULE_MESSAGE), _ -> {
        });

        assertThat(conversationTaskStorage.findByConversation(AGENT_ID, conversationId)).isEmpty();
    }
}