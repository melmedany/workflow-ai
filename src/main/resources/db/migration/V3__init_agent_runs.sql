SET TIME ZONE 'UTC';

CREATE TABLE agent_runs
(
    id              UUID PRIMARY KEY NOT NULL,
    agent_id        UUID             NOT NULL,
    conversation_id UUID             NOT NULL,
    trigger_source  VARCHAR          NOT NULL,
    task_id         UUID,
    started_at      TIMESTAMP        NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    status          VARCHAR          NOT NULL,
    error_message   TEXT
--  Foreign keys for agent_id and conversation_id was not added deliberately to avoid agent runs deletion on agent or conversation deletion
);

CREATE INDEX idx_agent_runs_agent_id ON agent_runs (agent_id);
CREATE INDEX idx_agent_runs_conversation_id ON agent_runs (conversation_id);