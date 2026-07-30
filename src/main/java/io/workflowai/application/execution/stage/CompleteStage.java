package io.workflowai.application.execution.stage;

import io.workflowai.domain.workflow.WorkflowStage;
import io.workflowai.domain.workflow.WorkflowState;
import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.domain.conversation.ConversationMessageRole;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.port.out.NotificationChannel;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class CompleteStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(CompleteStage.class);

    private final List<WorkflowEventStreamer> workflowEventStreamers;
    private final List<NotificationChannel> notificationChannels;

    public CompleteStage(List<WorkflowEventStreamer> workflowEventStreamers, List<NotificationChannel> notificationChannels) {
        this.workflowEventStreamers = workflowEventStreamers;
        this.notificationChannels = notificationChannels;
    }

    @Override
    public StageId stageId() {
        return StageId.COMPLETE;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.conversationCompleted(state.runId()));
        ConversationMessage message = new ConversationMessage(ConversationMessageRole.AGENT,
                state.generatedResponse().orElse(""), true);
        notificationChannels.forEach(channel -> channel.notify(state.agentProperties().id(), state.conversationId(), message));
        log.debug("[{}] Workflow complete for conversation [{}]", state.agentProperties().id(), state.conversationId());
        return Map.of();
    }
}