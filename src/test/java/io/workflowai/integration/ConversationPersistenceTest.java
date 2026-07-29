package io.workflowai.integration;

import io.workflowai.domain.conversation.Conversation;
import io.workflowai.domain.conversation.ConversationMessage;
import io.workflowai.domain.conversation.ConversationMessageRole;
import io.workflowai.application.port.out.ConversationStorage;
import io.workflowai.application.port.out.ConversationMessageStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Sql
class ConversationPersistenceTest extends IntegrationBase {

    // agent is defined via ConversationPersistenceTest.sql
    private final UUID AGENT_ID_1 = UUID.fromString("af13d6a3-d32f-4dd7-8672-48f81e01e209");
    private final UUID AGENT_ID_2 = UUID.fromString("12e4ede9-b954-4428-96c5-8051bea1c225");

    @Autowired
    ConversationStorage conversationStorage;

    @Autowired
    ConversationMessageStorage conversationMessageStorage;

    @Test
    void createAndRetrieveConversation() {
        Conversation created = conversationStorage.create(AGENT_ID_1, "Test conversation");

        assertThat(created.id()).isNotNull();
        assertThat(created.agentId()).isEqualTo(AGENT_ID_1);
        assertThat(created.title()).isEqualTo("Test conversation");

        Optional<Conversation> found = conversationStorage.findByAgentAndId(AGENT_ID_1, created.id());

        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(created.id());
    }

    @Test
    void listConversationsByAgent() {
        conversationStorage.create(AGENT_ID_1, "Agent conv 1");
        conversationStorage.create(AGENT_ID_1, "Agent conv 2");

        List<Conversation> conversations = conversationStorage.findByAgent(AGENT_ID_1);

        assertThat(conversations).hasSizeGreaterThanOrEqualTo(2);
        assertThat(conversations).allMatch(c -> AGENT_ID_1.equals(c.agentId()));
    }

    @Test
    void saveAndRetrieveMessages() {
        Conversation conversation = conversationStorage.create(AGENT_ID_1, "Message test");

        conversationMessageStorage.save(conversation.id(), AGENT_ID_1,
                new ConversationMessage(ConversationMessageRole.USER, "Hello", true));
        conversationMessageStorage.save(conversation.id(), AGENT_ID_1,
                new ConversationMessage(ConversationMessageRole.AGENT, "Hi there", true));

        List<ConversationMessage> messages = conversationMessageStorage.findByAgentIdAndConversationId(conversation.agentId(), conversation.id());

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo(ConversationMessageRole.USER);
        assertThat(messages.get(0).content()).isEqualTo("Hello");
        assertThat(messages.get(1).role()).isEqualTo(ConversationMessageRole.AGENT);
    }

    @Test
    void messagesAreIsolatedPerConversation() {
        Conversation conversation1 = conversationStorage.create(AGENT_ID_1, "Conversation 1");
        Conversation conversation2 = conversationStorage.create(AGENT_ID_2, "Conversation 2");

        conversationMessageStorage.save(conversation1.id(), AGENT_ID_1,
                new ConversationMessage(ConversationMessageRole.USER, "Conversation1 message", true));
        conversationMessageStorage.save(conversation2.id(), AGENT_ID_2,
                new ConversationMessage(ConversationMessageRole.USER, "Conversation2 message", true));

        assertThat(conversationMessageStorage.findByAgentIdAndConversationId(conversation1.agentId(), conversation1.id()))
                .hasSize(1);
        assertThat(conversationMessageStorage.findByAgentIdAndConversationId(conversation2.agentId(), conversation2.id()))
                .hasSize(1);
        assertThat(conversationMessageStorage.findByAgentIdAndConversationId(conversation1.agentId(), conversation1.id())
                .getFirst().content())
                .isEqualTo("Conversation1 message");
        assertThat(conversationMessageStorage.findByAgentIdAndConversationId(conversation2.agentId(), conversation2.id())
                .getFirst().content())
                .isEqualTo("Conversation2 message");
    }
}
