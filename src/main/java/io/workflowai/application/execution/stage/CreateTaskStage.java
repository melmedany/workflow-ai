package io.workflowai.application.execution.stage;

import io.workflowai.application.port.in.TaskUseCase;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.agent.TriggerSource;
import io.workflowai.domain.exceptions.InvalidScheduleException;
import io.workflowai.domain.exceptions.ScheduleTooFrequentException;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.SchedulingIntentDetector;
import io.workflowai.domain.workflow.RoutingDecision;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowStage;
import io.workflowai.domain.workflow.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class CreateTaskStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskStage.class);

    private final TaskUseCase taskUseCase;
    private final PersistResponseStage persistResponseStage;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public CreateTaskStage(TaskUseCase taskUseCase, PersistResponseStage persistResponseStage,
                           List<WorkflowEventStreamer> workflowEventStreamers) {
        this.taskUseCase = taskUseCase;
        this.persistResponseStage = persistResponseStage;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.CREATE_TASK;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.CREATE_TASK));

        if (state.triggerSource() == TriggerSource.SYSTEM_TRIGGER) {
            log.warn("[{}] Refusing to create a task from a system-triggered run", state.agentProperties().id());
            return refuse(state, "A scheduled run cannot create another scheduled task", state.userMessage());
        }

        RoutingDecision decision = state.routingDecision()
                .orElseThrow(() -> new IllegalStateException("CREATE_TASK reached without a routing decision"));

        String instruction = decision.scheduleInstruction();
        if (instruction == null || instruction.isBlank() || SchedulingIntentDetector.resemblesSchedulingRequest(instruction)) {
            return refuse(state, "Tasks cannot schedule other tasks", decision.extractedIntent());
        }

        try {
            ConversationTask task = taskUseCase.createOrUpdate(state.agentProperties().id(), state.conversationId(),
                    instruction, decision.cronExpression(), decision.runOnceAt() != null ? Instant.parse(decision.runOnceAt()) : null);

            workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.CREATE_TASK));
            String finalResponse = persistResponseStage.finalizeResponse(state, confirmationMessage(task));
            return Map.of(WorkflowState.KEY_GENERATED_RESPONSE, finalResponse, WorkflowState.KEY_VALIDATION_PASSED, true);
        } catch (InvalidScheduleException ex) {
            log.debug("[{}] Schedule could not be parsed: {}", state.agentProperties().id(), ex.getMessage());
            return clarify(state, ex.getMessage(), decision.extractedIntent());
        } catch (ScheduleTooFrequentException ex) {
            log.debug("[{}] Schedule rejected: {}", state.agentProperties().id(), ex.getMessage());
            return refuse(state, ex.getMessage(), decision.extractedIntent());
        }
    }

    private Map<String, Object> refuse(WorkflowState state, String reason, String extractedIntent) {
        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.CREATE_TASK));
        return Map.of(WorkflowState.KEY_ROUTING_DECISION, RoutingDecision.refuse(reason, extractedIntent));
    }

    private Map<String, Object> clarify(WorkflowState state, String reason, String extractedIntent) {
        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.CREATE_TASK));
        String question = "I couldn't understand that schedule — could you restate when this should run (e.g. \"every day at 9am\")?";
        return Map.of(WorkflowState.KEY_ROUTING_DECISION, RoutingDecision.clarify(reason, extractedIntent, question));
    }

    private String confirmationMessage(ConversationTask task) {
        return """
                Scheduled. This will run on the schedule you provided.
                Each time it runs, I will: %s
                """.formatted(task.definition().instruction()).trim();
    }
}