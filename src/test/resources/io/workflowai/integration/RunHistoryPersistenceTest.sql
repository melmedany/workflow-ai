TRUNCATE agents CASCADE;

INSERT INTO agents (id, details, llm_properties, workflow_policy_properties)
VALUES ('d60ab6a0-6ef9-4ef4-b9b4-f4d21006dd9a',
        '{
          "displayName": "Run History Test Agent",
          "description": "Agent for run-history integration testing",
          "enabled": true
        }'::jsonb,
        '{
          "providerId": "Ollama",
          "model": "deepseek-r1:8b",
          "agentPrompt": "",
          "temperature": 0.7,
          "memoryEnabled": false
        }'::jsonb,
        '{
          "supportedCapabilities": [],
          "fallbackFailedToProcess": "I cannot process that right now."
        }'::jsonb);

INSERT INTO conversations (id, agent_id, title)
VALUES ('f6e17be6-dbb4-4391-a66d-32e01edac49c', 'd60ab6a0-6ef9-4ef4-b9b4-f4d21006dd9a', 'Run history conversation');
