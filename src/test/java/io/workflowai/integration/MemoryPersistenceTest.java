package io.workflowai.integration;

import io.workflowai.domain.model.ConversationMessage;
import io.workflowai.ports.outbound.AgentMemoryStoragePort;
import io.workflowai.ports.outbound.ConversationStoragePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql
class MemoryPersistenceTest extends IntegrationBase {

    // agent is defined via ChatEndpointTest.sql
    private final UUID AGENT_ID = UUID.fromString("c7d5842d-cece-490b-9fcc-c6865611e94b");

    @Autowired
    ConversationStoragePort conversationStoragePort;

    @Autowired
    AgentMemoryStoragePort agentMemoryStoragePort;

    @Test
    void memoryDoesNotLeakIntoDifferentConversations() {
        UUID conversationId = conversationStoragePort.create(AGENT_ID, "agent memory test").id();

        agentMemoryStoragePort.add(conversationId, AGENT_ID, "agent remembered: refactor with SOLID");

        List<ConversationMessage> agentMemory = agentMemoryStoragePort.getHistory(conversationId, AGENT_ID);

        assertThat(agentMemory).hasSize(1);
        assertThat(agentMemory.getFirst().content()).contains("SOLID");

        List<ConversationMessage> otherConversation = agentMemoryStoragePort.getHistory(UUID.randomUUID(), AGENT_ID);
        assertThat(otherConversation).isEmpty();
    }

    @Test
    void clearMemoryForConversation() {
        UUID conversationId = conversationStoragePort.create(AGENT_ID, "Clear test").id();
        agentMemoryStoragePort.add(conversationId, AGENT_ID, "some memory");

        assertThat(agentMemoryStoragePort.getHistory(conversationId, AGENT_ID)).hasSize(1);

        agentMemoryStoragePort.clear(conversationId);

        assertThat(agentMemoryStoragePort.getHistory(conversationId, AGENT_ID)).isEmpty();
    }
}
