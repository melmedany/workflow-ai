package io.workflowai.application.execution.stage;

import io.workflowai.application.execution.WorkflowState;
import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.domain.conversation.ConversationMessageRole;
import io.workflowai.domain.workflow.StageId;
import io.workflowai.application.port.out.ConversationMessageStorage;
import io.workflowai.application.port.out.WorkflowEventStreamer;

import java.util.List;

/**
 * Shared by every stage that produces a final response (EXECUTE_WORKFLOW/SELF_VERIFICATION and the
 * GENERATE_CLARIFICATION/REDIRECT/GREETING/REFUSAL branches). The candidate text has already passed
 * through the output guardrail — either automatically inside the {@code ChatProvider} call that
 * produced it, or explicitly for the one path that doesn't call a model (see
 * {@link GenerateClarificationStage}) — so this stage only persists and emits, it does not re-check.
 * Persisted before a single token reaches the client: if the SSE connection drops while streaming,
 * the response is already durably saved.
 */
public class PersistResponseStage {

    private final ConversationMessageStorage conversationMessageStorage;
    private final List<WorkflowEventStreamer> workflowEventStreamers;

    public PersistResponseStage(ConversationMessageStorage conversationMessageStorage, List<WorkflowEventStreamer> workflowEventStreamers) {
        this.conversationMessageStorage = conversationMessageStorage;
        this.workflowEventStreamers = workflowEventStreamers;
    }

    public String finalizeResponse(WorkflowState state, String finalResponse) {
        workflowEventStreamers.forEach(s -> s.stageStarted(state.runId(), StageId.PERSIST_RESPONSE));
        conversationMessageStorage.save(
                state.conversationId(),
                state.agentProperties().id(),
                new ConversationMessage(ConversationMessageRole.AGENT, finalResponse, false));
        workflowEventStreamers.forEach(s -> s.stageCompleted(state.runId(), StageId.PERSIST_RESPONSE));

        // Emitted as simulated token chunks over the existing WorkflowEvent.Token schema, so chat.js
        // needs no changes: it just receives a burst of tokens close together instead of
        // generation-paced ones.
        workflowEventStreamers.forEach(s -> s.token(state.runId(), finalResponse));
        workflowEventStreamers.forEach(s -> s.responseCompleted(state.runId(), finalResponse));
        return finalResponse;
    }
}