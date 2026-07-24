CREATE TABLE conversations
(
    id         UUID PRIMARY KEY NOT NULL,
    agent_id   UUID             NOT NULL,
    title      VARCHAR(500)     NOT NULL,
    created_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE
);

CREATE TABLE messages
(
    id              UUID PRIMARY KEY NOT NULL,
    conversation_id UUID             NOT NULL,
    agent_id        UUID             NOT NULL,
    role            VARCHAR(20)      NOT NULL,
    content         TEXT             NOT NULL,
    add_to_memory   BOOLEAN          NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ      NOT NULL DEFAULT now(),
    FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE
);

CREATE TABLE agent_memory
(
    id              UUID PRIMARY KEY NOT NULL,
    conversation_id UUID             NOT NULL,
    agent_id        UUID             NOT NULL,
    content         TEXT             NOT NULL,
    created_at      TIMESTAMP        NOT NULL DEFAULT now(),
    FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE,
    UNIQUE (conversation_id, agent_id)
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
CREATE INDEX idx_agent_memory_conversation_id ON agent_memory (conversation_id);
CREATE INDEX idx_conversations_agent_id_name ON conversations (agent_id);