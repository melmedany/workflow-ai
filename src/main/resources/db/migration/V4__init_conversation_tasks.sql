SET TIME ZONE 'UTC';

CREATE TABLE conversation_tasks
(
    id              UUID PRIMARY KEY NOT NULL,
    agent_id        UUID             NOT NULL,
    conversation_id UUID             NOT NULL,
    definition      JSONB            NOT NULL,
    schedule        JSONB            NOT NULL,
    job_id          VARCHAR,
    last_run_id     UUID,
    created_at      TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP        NOT NULL DEFAULT now(),
    FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE,
    FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE
);

CREATE INDEX idx_conversation_tasks_conversation_id ON conversation_tasks (conversation_id);