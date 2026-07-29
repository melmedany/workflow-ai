package io.workflowai.integration;

import io.workflowai.adapter.out.persistence.agent.run.AgentRunEntity;
import io.workflowai.adapter.out.persistence.agent.run.AgentRunRepository;
import io.workflowai.domain.run.AgentRunStatus;
import io.workflowai.application.port.out.AgentRunTracker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static io.workflowai.domain.run.TriggerSource.SYSTEM_TRIGGER;
import static io.workflowai.domain.run.TriggerSource.USER_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Sql
class DatabaseAgentRunTrackerAdapterTest extends IntegrationBase {

    // agent is defined via DatabaseAgentRunTrackerAdapterTest.sql
    private static final UUID AGENT_ID = UUID.fromString("d60ab6a0-6ef9-4ef4-b9b4-f4d21006dd9a");
    private static final UUID CONVERSATION_ID = UUID.fromString("f6e17be6-dbb4-4391-a66d-32e01edac49c");

    @Autowired
    private AgentRunTracker agentsRunTracker;
    @Autowired
    private AgentRunRepository agentRunsRepository;

    @Test
    void runHistoryPersistsCompletedAndFailedRuns() {
        UUID completedRunId = agentsRunTracker.start(USER_MESSAGE, AGENT_ID, CONVERSATION_ID);
        agentsRunTracker.complete(completedRunId);
        UUID failedRunId = agentsRunTracker.start(SYSTEM_TRIGGER, AGENT_ID, CONVERSATION_ID);
        agentsRunTracker.fail(failedRunId, "Provider unavailable");

        AgentRunEntity completed = run(completedRunId);
        assertEquals(USER_MESSAGE, completed.triggerSource());
        assertEquals(AgentRunStatus.COMPLETED, completed.status());
        assertNotNull(completed.startedAt());
        assertNotNull(completed.completedAt());

        AgentRunEntity failed = run(failedRunId);
        assertEquals(SYSTEM_TRIGGER, failed.triggerSource());
        assertEquals(AgentRunStatus.FAILED, failed.status());
        assertEquals("Provider unavailable", failed.errorMessage());
        assertNotNull(failed.completedAt());
    }

    private AgentRunEntity run(UUID runId) {
        return agentRunsRepository.findById(runId).orElseThrow();
    }
}
