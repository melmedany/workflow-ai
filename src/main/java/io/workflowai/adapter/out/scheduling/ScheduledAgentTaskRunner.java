package io.workflowai.adapter.out.scheduling;

import io.workflowai.application.execution.AgentRequest;
import io.workflowai.application.port.in.AgentUseCase;
import io.workflowai.application.port.out.ConversationTaskStorage;
import io.workflowai.domain.task.ConversationTask;
import io.workflowai.domain.task.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ScheduledAgentTaskRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledAgentTaskRunner.class);

    private final ConversationTaskStorage storage;
    private final AgentUseCase agentUseCase;

    public ScheduledAgentTaskRunner(ConversationTaskStorage storage, AgentUseCase agentUseCase) {
        this.storage = storage;
        this.agentUseCase = agentUseCase;
    }

    public void run(UUID taskId) {
        ConversationTask task = storage.findById(taskId).orElse(null);
        if (task == null || task.schedule().status() != TaskStatus.ACTIVE) {
            log.debug("Skipping scheduled task [{}], not active", taskId);
            return;
        }

        UUID runId = agentUseCase.trigger(
                AgentRequest.systemTrigger(task.agentId(), task.conversationId(), task.id(), task.definition().instruction()),
                _ -> {
                });

        if (task.schedule().runOnceAt() != null) {
            storage.updateAfterRun(taskId, runId);
            storage.updateStatus(taskId, TaskStatus.COMPLETED);
            return;
        }

        storage.updateAfterRun(taskId, runId);
    }
}