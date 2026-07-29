package io.workflowai.bootstrap.config;

import io.workflowai.application.execution.ResponseValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class WorkflowBeansConfig {

    @Bean
    ResponseValidator responseValidator(JsonMapper jsonMapper) {
        return new ResponseValidator(jsonMapper);
    }
}