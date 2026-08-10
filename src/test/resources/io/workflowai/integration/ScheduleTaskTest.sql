TRUNCATE agents CASCADE;

INSERT INTO agents (id, workflow_id, details, chat_properties, workflow_policy)
VALUES ('29014fc2-5616-4ea2-8d15-0fe8c42afea5',
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
