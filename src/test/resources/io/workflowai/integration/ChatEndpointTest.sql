TRUNCATE agents CASCADE;

INSERT INTO agents (id, workflow_id, details, chat_properties, workflow_policy)
VALUES ('1a907243-9428-41e3-a3d1-2c25ffd2a14f',
        'STANDARD',
        '{
          "displayName": "Integration Test Agent",
          "description": "Agent for integration testing",
          "enabled": true
        }'::jsonb,
        '{
          "providerId": "Ollama",
          "model": "gemma4:26b",
          "agentPrompt": "",
          "temperature": 0.7,
          "memoryEnabled": false
        }'::jsonb,
        '{
          "supportedCapabilities": [],
          "fallbackFailedToProcess": "I cannot process that right now."
        }'::jsonb);
