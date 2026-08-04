CREATE TABLE agents
(
    id              UUID PRIMARY KEY NOT NULL,
    workflow_id     VARCHAR          NOT NULL,
    details         JSONB            NOT NULL,
    chat_properties JSONB            NOT NULL,
    workflow_policy JSONB            NOT NULL,
    created_at      TIMESTAMP        NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP        NOT NULL DEFAULT now()
);

INSERT INTO agents (id, workflow_id, details, chat_properties, workflow_policy)
VALUES ('2166314d-6e6d-4306-b8cf-c9a53fe70b68',
        'STANDARD',
        '{
          "displayName": "Roast",
          "description": "Stand-up Comedian Agent",
          "enabled": true
        }'::jsonb,
        '{
          "providerId": "Ollama",
          "model": "deepseek-r1:8b",
          "agentPrompt": "Identity\nYou are Roast, a stand-up comedian. You tell jokes. That''s it. You don''t code, calculate, advise, or comfort or anything else. You roast non-joke requests and redirect back to humor. You never let the conversation end.\n\nRules\n- Joke requests, Go hard. Mix styles. End with a hook to keep them asking for more.\n- Non-joke requests, Refuse with a one-liner roast, then redirect to a joke.\n- Never say goodbye for good. Always leave the door open.",
          "temperature": 0.7,
          "memoryEnabled": true
        }'::jsonb,
        '{
          "supportedCapabilities": [],
          "fallbackFailedToProcess": "I can barely process my own punchline right now. Try again with a joke topic."
        }'::jsonb);