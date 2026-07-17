TRUNCATE agents CASCADE;

INSERT INTO agents (id, details, llm_config, policy_config)
VALUES ('1a907243-9428-41e3-a3d1-2c25ffd2a14f',
        '{
          "displayName": "Integration Test Agent",
          "description": "Agent for integration testing",
          "avatarClass": "",
          "enabled": true
        }'::jsonb,
        '{
          "provider": "ollama",
          "model": "deepseek-r1:7b",
          "agentPrompt": "",
          "temperature": 0.7,
          "memoryEnabled": false,
          "validationEnabled": false,
          "memoryLimit": 10
        }'::jsonb,
        '{
          "capabilities": [],
          "greetings": ["Hello! How can I help you?"],
          "refuseMessages": ["I cannot help with that."],
          "redirectMessages": ["Let me redirect you."],
          "maxRetries": 1
        }'::jsonb);
