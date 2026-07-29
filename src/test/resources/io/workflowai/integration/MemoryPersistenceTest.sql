TRUNCATE agents CASCADE;

INSERT INTO agents (id, details, chat_properties, workflow_policy_properties)
VALUES ('c7d5842d-cece-490b-9fcc-c6865611e94b',
        '{
          "displayName": "Integration Test Agent",
          "description": "Agent for integration testing",
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