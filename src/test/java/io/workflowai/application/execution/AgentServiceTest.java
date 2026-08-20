package io.workflowai.application.execution;

import io.workflowai.application.execution.workflow.WorkflowFactory;
import io.workflowai.application.port.in.ConversationUseCase;
import io.workflowai.application.port.out.AgentDefinitionStorage;
import io.workflowai.application.port.out.AgentRunTracker;
import io.workflowai.application.port.out.WorkflowEventStreamer;
import io.workflowai.domain.agent.AgentDefinition;
import io.workflowai.domain.agent.AgentDetails;
import io.workflowai.domain.agent.ChatProperties;
import io.workflowai.domain.agent.ChatProviderId;
import io.workflowai.domain.agent.TriggerSource;
import io.workflowai.domain.workflow.Workflow;
import io.workflowai.domain.workflow.WorkflowId;
import io.workflowai.domain.workflow.WorkflowPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceTest {

    private final AgentDefinitionStorage definitionStorage = mock();
    private final WorkflowFactory workflowFactory = mock();
    private final AgentRunTracker agentRunTracker = mock();
    private final WorkflowEventStreamer workflowEventStreamer = mock();
    private final ConversationUseCase conversationService = mock();
    private final Workflow workflow = mock();

    private final UUID agentId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @Test
    void aSuccessfulRunCompletesAndAlwaysRevokesTheConsumer() {
        AgentService service = service();
        AgentRequest request = AgentRequest.userMessage(agentId, conversationId, "hello");
        when(agentRunTracker.start(TriggerSource.USER_MESSAGE, agentId, conversationId, null)).thenReturn(runId);

        UUID result = service.trigger(request, _ -> { });

        assertThat(result).isEqualTo(runId);
        verify(agentRunTracker).complete(runId);
        verify(agentRunTracker, never()).fail(any(), any());
        verify(workflowEventStreamer).revokeConsumer(runId);
    }

    @Test
    void anExceptionFromTheWorkflowIsRecordedAsAFailureAndRethrownUnchanged() {
        AgentService service = service();
        AgentRequest request = AgentRequest.userMessage(agentId, conversationId, "hello");
        when(agentRunTracker.start(TriggerSource.USER_MESSAGE, agentId, conversationId, null)).thenReturn(runId);
        RuntimeException workflowFailure = new RuntimeException("workflow blew up");
        doThrow(workflowFailure).when(workflow)
                .execute(eq(runId), eq(conversationId), eq(TriggerSource.USER_MESSAGE), eq("hello"));

        assertThatThrownBy(() -> service.trigger(request, _ -> { }))
                .isSameAs(workflowFailure);

        verify(agentRunTracker).fail(runId, "workflow blew up");
        verify(agentRunTracker, never()).complete(runId);
        verify(workflowEventStreamer).revokeConsumer(runId);
    }

    private AgentService service() {
        when(definitionStorage.findAll()).thenReturn(List.of());
        AgentService service = new AgentService(definitionStorage, workflowFactory, agentRunTracker,
                workflowEventStreamer, conversationService);
        when(definitionStorage.findById(agentId)).thenReturn(agentDefinition());
        when(workflowFactory.build(eq(WorkflowId.STANDARD), any())).thenReturn(workflow);
        return service;
    }

    private AgentDefinition agentDefinition() {
        return new AgentDefinition(
                agentId,
                new AgentDetails("agent", "description", true),
                WorkflowId.STANDARD,
                new ChatProperties(ChatProviderId.Ollama, "agent-model", "system", 0.5, false),
                new WorkflowPolicy(List.of(), null, "fallback"));
    }
}
