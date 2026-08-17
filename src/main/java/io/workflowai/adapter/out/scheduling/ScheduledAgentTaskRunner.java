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

    public void run(UUID agentId, UUID conversationId, UUID taskId) {
        ConversationTask task = storage.findActiveTask(agentId, conversationId, taskId).orElse(null);
        if (task == null || task.schedule().status() != TaskStatus.ACTIVE) {
            log.debug("Skipping scheduled task [{}], not active", taskId);
            return;
        }

        UUID runId = agentUseCase.trigger(
                AgentRequest.systemTrigger(task.agentId(), task.conversationId(), task.id(),
                        "SCHEDULED: %s".formatted(task.definition().instruction())),
                _ -> {
                });

        if (task.runOnce()) {
            storage.updateAfterRun(task.agentId(), task.conversationId(), taskId, runId);
            storage.updateStatus(task.agentId(), task.conversationId(), taskId, TaskStatus.COMPLETED);
            return;
        }

        storage.updateAfterRun(task.agentId(), task.conversationId(), taskId, runId);
    }
}