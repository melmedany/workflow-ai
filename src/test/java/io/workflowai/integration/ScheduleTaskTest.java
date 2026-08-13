package io.workflowai.integration;

import io.workflowai.application.execution.AgentRequest;
import io.workflowai.application.execution.ChatProviderRegistry;
import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.application.port.in.ConversationUseCase;
import io.workflowai.application.port.out.ChatCompletionRequest;
import io.workflowai.application.port.out.ChatProvider;
import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.domain.task.ConversationTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static io.workflowai.domain.agent.ChatProviderId.Ollama;
import static io.workflowai.domain.task.ConversationTask.TaskSchedule.ScheduleType.RECURRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Sql
class ScheduleTaskTest extends IntegrationBase {

    // agent is defined via ScheduleTaskTest.sql
    private static final UUID AGENT_ID = UUID.fromString("29014fc2-5616-4ea2-8d15-0fe8c42afea5");
    private static final String SCHEDULE_MESSAGE = "/schedule every day at 9am, summarize open PRs";
    private static final String CLASSIFICATION_JSON = """
            {"decisionMode":"EXECUTE","detectedTopics":[],"extractedIntent":"schedule a daily PR summary",
            "clarificationQuestion":null,"reason":"clear recurring request","duration":"P1D",
            "scheduleType":"RECURRING","scheduleInstruction":"Summarize open PRs"}
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
    void setUp() {
        ChatProvider ollama = mock();
        when(ollama.getId()).thenReturn(Ollama);
        when(ollama.stream(any(ChatCompletionRequest.class), any(Consumer.class))).thenReturn("Test response");
        when(ollama.call(any(ChatCompletionRequest.class))).thenReturn(CLASSIFICATION_JSON);

        when(chatProviderRegistry.get(Ollama)).thenReturn(ollama);
        when(chatProviderRegistry.supportedChatProviders()).thenReturn(Map.of(Ollama, Set.of()));
    }

    @Test
    void scheduleMessage_createsExactlyOneTask_andUpsertsOnRepeat() {
        UUID conversationId = conversationUseCase.createConversation(AGENT_ID, "hi").id();

        agentUseCase.trigger(AgentRequest.userMessage(AGENT_ID, conversationId, SCHEDULE_MESSAGE), _ -> {
        });

        List<ConversationTask> afterFirst = conversationTaskStorage.findByConversation(AGENT_ID, conversationId);
        assertThat(afterFirst).hasSize(1);
        ConversationTask task = afterFirst.getFirst();
        assertThat(task.schedule().type()).isEqualTo(RECURRING);
        assertThat(task.schedule().duration()).isEqualTo(Duration.parse("P1D"));
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