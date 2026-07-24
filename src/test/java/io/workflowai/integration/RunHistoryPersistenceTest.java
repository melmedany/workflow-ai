package io.workflowai.integration;

import io.workflowai.adapters.outbound.persistence.agentrun.AgentRunEntity;
import io.workflowai.adapters.outbound.persistence.agentrun.AgentRunRepository;
import io.workflowai.domain.model.AgentRunStatus;
import io.workflowai.ports.outbound.RunHistoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;

import static io.workflowai.domain.model.TriggerSource.SYSTEM_TRIGGER;
import static io.workflowai.domain.model.TriggerSource.USER_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Sql
class RunHistoryPersistenceTest extends IntegrationBase {

    // agent is defined via RunHistoryPersistenceTest.sql
    private static final UUID AGENT_ID = UUID.fromString("d60ab6a0-6ef9-4ef4-b9b4-f4d21006dd9a");
    private static final UUID CONVERSATION_ID = UUID.fromString("f6e17be6-dbb4-4391-a66d-32e01edac49c");

    @Autowired
    private RunHistoryPort runHistoryPort;
    @Autowired
    private AgentRunRepository agentRunsRepository;

    @Test
    void runHistoryPersistsCompletedAndFailedRuns() {
        UUID completedRunId = runHistoryPort.start(USER_MESSAGE, AGENT_ID, CONVERSATION_ID);
        runHistoryPort.complete(completedRunId);
        UUID failedRunId = runHistoryPort.start(SYSTEM_TRIGGER, AGENT_ID, CONVERSATION_ID);
        runHistoryPort.fail(failedRunId, "Provider unavailable");

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
