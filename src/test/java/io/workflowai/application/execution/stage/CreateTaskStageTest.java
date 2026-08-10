package io.workflowai.application.execution.stage;

import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.domain.agent.AgentProperties;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.agent.TriggerSource;
import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.domain.exceptions.InvalidScheduleException;
import io.workflowai.domain.exceptions.ScheduleTooFrequentException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.ConversationTask.TaskDefinition;
import io.workflowai.domain.task.ConversationTask.TaskRunInfo;
import io.workflowai.domain.task.ConversationTask.TaskSchedule;
import io.workflowai.domain.task.TaskStatus;
import io.workflowai.domain.workflow.DecisionMode;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import io.workflowai.domain.workflow.WorkflowState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreateTaskStageTest {

    @Test
    void createsTaskAndConfirmsInPlainLanguage() {
        RecordingTaskUseCase taskUseCase = new RecordingTaskUseCase();
        RecordingConversationMessageStorage messages = new RecordingConversationMessageStorage();
        CreateTaskStage stage = stage(taskUseCase, messages);

        WorkflowState state = state(TriggerSource.USER_MESSAGE, executeDecision(
                "0 9 * * *", "Summarize open PRs"));

        Map<String, Object> result = stage.execute(state);

        assertThat(taskUseCase.created).hasSize(1);
        assertThat(messages.messages()).singleElement()
                .extracting(ConversationMessage::content)
                .satisfies(content -> assertThat(content).contains("Summarize open PRs"));
        assertThat(result.get(WorkflowState.KEY_VALIDATION_PASSED)).isEqualTo(true);
    }

    @Test
    void systemTriggerCanNeverCreateATask() {
        RecordingTaskUseCase taskUseCase = new RecordingTaskUseCase();
        CreateTaskStage stage = stage(taskUseCase, new RecordingConversationMessageStorage());

        WorkflowState state = state(TriggerSource.SYSTEM_TRIGGER, executeDecision(
                "0 9 * * *", "Summarize open PRs"));

        Map<String, Object> result = stage.execute(state);

        assertThat(taskUseCase.created).isEmpty();
        assertThat(decisionMode(result)).isEqualTo(DecisionMode.REFUSE);
    }

    @Test
    void refusesWhenExtractedInstructionStillResemblesASchedulingRequest() {
        RecordingTaskUseCase taskUseCase = new RecordingTaskUseCase();
        CreateTaskStage stage = stage(taskUseCase, new RecordingConversationMessageStorage());

        WorkflowState state = state(TriggerSource.USER_MESSAGE, executeDecision(
                "0 9 * * *", "/schedule every hour, ping me"));

        Map<String, Object> result = stage.execute(state);

        assertThat(taskUseCase.created).isEmpty();
        assertThat(decisionMode(result)).isEqualTo(DecisionMode.REFUSE);
    }

    @Test
    void clarifiesWhenScheduleCannotBeParsed() {
        RecordingTaskUseCase taskUseCase = new RecordingTaskUseCase();
        taskUseCase.failure = new InvalidScheduleException("bad cron");
        CreateTaskStage stage = stage(taskUseCase, new RecordingConversationMessageStorage());

        WorkflowState state = state(TriggerSource.USER_MESSAGE, executeDecision(
                "not-a-cron", "Summarize open PRs"));

        Map<String, Object> result = stage.execute(state);

        assertThat(decisionMode(result)).isEqualTo(DecisionMode.CLARIFY);
        RoutingDecision decision = (RoutingDecision) result.get(WorkflowState.KEY_ROUTING_DECISION);
        assertThat(decision.clarificationQuestion()).isNotBlank();
    }

    @Test
    void refusesWhenScheduleIsTooFrequent() {
        RecordingTaskUseCase taskUseCase = new RecordingTaskUseCase();
        taskUseCase.failure = new ScheduleTooFrequentException("too frequent");
        CreateTaskStage stage = stage(taskUseCase, new RecordingConversationMessageStorage());

        WorkflowState state = state(TriggerSource.USER_MESSAGE, executeDecision(
                "* * * * * *", "Summarize open PRs"));

        Map<String, Object> result = stage.execute(state);

        assertThat(decisionMode(result)).isEqualTo(DecisionMode.REFUSE);
    }

    private DecisionMode decisionMode(Map<String, Object> result) {
        return ((RoutingDecision) result.get(WorkflowState.KEY_ROUTING_DECISION)).decisionMode();
    }

    private CreateTaskStage stage(TaskUseCase taskUseCase, ConversationMessageStorage messages) {
        PersistResponseStage persistResponseStage = new PersistResponseStage(messages, List.of());
        return new CreateTaskStage(taskUseCase, persistResponseStage, List.of());
    }

    private RoutingDecision executeDecision(String cron, String scheduleInstruction) {
        return new RoutingDecision(DecisionMode.EXECUTE, List.of(), "schedule request", null,
                "clear recurring request", cron, null, scheduleInstruction);
    }

    private WorkflowState state(TriggerSource triggerSource, RoutingDecision decision) {
        return new WorkflowState(Map.of(
                WorkflowState.KEY_RUN_ID, UUID.randomUUID(),
                WorkflowState.KEY_CONVERSATION_ID, UUID.randomUUID(),
                WorkflowState.KEY_USER_MESSAGE, "every day at 9am, summarize open PRs",
                WorkflowState.KEY_TRIGGER_SOURCE, triggerSource,
                WorkflowState.KEY_SCHEDULING_REQUESTED, true,
                WorkflowState.KEY_AGENT_PROPERTIES, agentProperties(),
                WorkflowState.KEY_ROUTING_DECISION, decision));
    }

    private AgentProperties agentProperties() {
        return new AgentProperties(UUID.randomUUID(), "agent", "description", true,
                WorkflowId.STANDARD, ChatProviderId.Ollama, "agent-model", 0.9, "system", true,
                new WorkflowPolicy(List.of(), null, "fallback"));
    }

    private static final class RecordingTaskUseCase implements TaskUseCase {
        private final List<ConversationTask> created = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public ConversationTask createOrUpdate(UUID agentId, UUID conversationId, String instruction, String cronExpression, Instant runOnceAt) {
            if (failure != null) {
                throw failure;
            }
            TaskDefinition definition = new TaskDefinition("name", "key", instruction);
            TaskSchedule schedule = new TaskSchedule(cronExpression, runOnceAt, TaskStatus.ACTIVE);
            TaskRunInfo runInfo = new TaskRunInfo(null, null);

            ConversationTask task = new ConversationTask(null, agentId, conversationId, definition,
                    schedule, runInfo, Instant.now(), Instant.now());
            created.add(task);
            return task;
        }

        @Override
        public void pause(UUID taskId) {
        }

        @Override
        public void resume(UUID taskId) {
        }

        @Override
        public void cancel(UUID taskId) {
        }

        @Override
        public List<ConversationTask> listByConversation(UUID agentId, UUID conversationId) {
            return List.copyOf(created);
        }
    }

    private record RecordingConversationMessageStorage(
            List<ConversationMessage> messages) implements ConversationMessageStorage {
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
}