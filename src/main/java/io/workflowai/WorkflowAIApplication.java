package io.workflowai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkflowAIApplication {
    static void main(String[] args) {
        SpringApplication.run(WorkflowAIApplication.class, args);
    }
}
