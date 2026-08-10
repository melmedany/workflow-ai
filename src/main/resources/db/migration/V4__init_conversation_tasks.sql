SET TIME ZONE 'UTC';

CREATE TABLE conversation_tasks
(
    id              UUID PRIMARY KEY NOT NULL,
    agent_id        UUID             NOT NULL,
    conversation_id UUID             NOT NULL,
    name            VARCHAR          NOT NULL,
    intent_key      VARCHAR          NOT NULL,
    instruction     TEXT             NOT NULL,
    cron_expression VARCHAR,
    run_once_at     VARCHAR,
    status          VARCHAR          NOT NULL,
    last_run_id     UUID,
    created_at      TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP        NOT NULL DEFAULT now(),
    FOREIGN KEY (agent_id) REFERENCES agents (id) ON DELETE CASCADE,
    FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    UNIQUE (conversation_id, agent_id, intent_key)
);

CREATE INDEX idx_conversation_tasks_conversation_id ON conversation_tasks (conversation_id);