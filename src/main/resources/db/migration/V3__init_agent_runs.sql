CREATE TABLE agent_runs
(
    id              UUID PRIMARY KEY NOT NULL,
    agent_id        UUID             NOT NULL,
    conversation_id UUID,
    trigger_source  VARCHAR(30)      NOT NULL,
    started_at      TIMESTAMP        NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    status          VARCHAR(20)      NOT NULL,
    error_message   TEXT
--  Foreign keys for agent_id and conversation_id was not added deliberately to avoid agent runs deletion on agent or conversation deletion
);

CREATE INDEX idx_agent_runs_agent_id ON agent_runs (agent_id);
CREATE INDEX idx_agent_runs_conversation_id ON agent_runs (conversation_id);