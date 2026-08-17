package io.workflowai.application.execution.stage;

import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.domain.conversation.ConversationMessageRole;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.domain.workflow.WorkflowStage;
import io.workflowai.domain.workflow.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.workflowai.domain.agent.TriggerSource.SYSTEM_TRIGGER;
import static io.workflowai.domain.conversation.ConversationMessageRole.SYSTEM;
import static io.workflowai.domain.conversation.ConversationMessageRole.USER;

public class PersistUserMessageStage implements WorkflowStage {

    private static final Logger log = LoggerFactory.getLogger(PersistUserMessageStage.class);

    private final ConversationMessageStorage conversationMessageStorage;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public PersistUserMessageStage(ConversationMessageStorage conversationMessageStorage, List<WorkflowEventStreamer> workflowEventStreamers) {
        this.conversationMessageStorage = conversationMessageStorage;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    @Override
    public StageId stageId() {
        return StageId.PERSIST_USER_MESSAGE;
    }

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.PERSIST_USER_MESSAGE));

        ConversationMessageRole messageRole = state.triggerSource() == SYSTEM_TRIGGER ? SYSTEM : USER;

        conversationMessageStorage.save(
                state.conversationId(),
                state.agentProperties().id(),
                new ConversationMessage(messageRole, state.userMessage(), true));
        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.PERSIST_USER_MESSAGE));
        log.debug("[{}] User message persisted for conversation [{}]", state.agentProperties().id(), state.conversationId());
        return Map.of();
    }
}
