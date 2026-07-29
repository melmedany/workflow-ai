package io.workflowai.integration;

import io.workflowai.application.port.out.AgentMemoryStorage;
import io.workflowai.application.port.out.ConversationStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql
class MemoryPersistenceTest extends IntegrationBase {

    // agent is defined via MemoryPersistenceTest.sql
    private final UUID AGENT_ID = UUID.fromString("c7d5842d-cece-490b-9fcc-c6865611e94b");

    @Autowired
    ConversationStorage conversationStorage;

    @Autowired
    AgentMemoryStorage agentMemoryStorage;

    @Test
    void memoryDoesNotLeakIntoDifferentConversations() {
        UUID conversationId = conversationStorage.create(AGENT_ID, "agent memory test").id();

        agentMemoryStorage.replace(conversationId, AGENT_ID, "agent remembered: refactor with SOLID");

        Optional<String> agentMemory = agentMemoryStorage.getMemory(conversationId, AGENT_ID);

        assertThat(agentMemory).contains("agent remembered: refactor with SOLID");

        assertThat(agentMemoryStorage.getMemory(UUID.randomUUID(), AGENT_ID)).isEmpty();
    }

    @Test
    void clearMemoryForConversation() {
        UUID conversationId = conversationStorage.create(AGENT_ID, "Clear test").id();
        agentMemoryStorage.replace(conversationId, AGENT_ID, "some memory");

        assertThat(agentMemoryStorage.getMemory(conversationId, AGENT_ID)).contains("some memory");

        agentMemoryStorage.clear(conversationId);

        assertThat(agentMemoryStorage.getMemory(conversationId, AGENT_ID)).isEmpty();
    }
}
