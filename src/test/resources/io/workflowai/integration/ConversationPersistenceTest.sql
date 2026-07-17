TRUNCATE agents CASCADE;

INSERT INTO agents (id, details, llm_config, policy_config)
VALUES ('af13d6a3-d32f-4dd7-8672-48f81e01e209',
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

INSERT INTO agents (id, details, llm_config, policy_config)
VALUES ('12e4ede9-b954-4428-96c5-8051bea1c225',
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
