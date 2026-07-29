TRUNCATE agents CASCADE;

INSERT INTO agents (id, details, chat_properties, workflow_policy_properties)
VALUES ('af13d6a3-d32f-4dd7-8672-48f81e01e209',
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

INSERT INTO agents (id, details, chat_properties, workflow_policy_properties)
VALUES ('12e4ede9-b954-4428-96c5-8051bea1c225',
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
