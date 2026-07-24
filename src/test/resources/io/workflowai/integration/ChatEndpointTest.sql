TRUNCATE agents CASCADE;

INSERT INTO agents (id, details, llm_properties, workflow_policy_properties)
VALUES ('1a907243-9428-41e3-a3d1-2c25ffd2a14f',
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
