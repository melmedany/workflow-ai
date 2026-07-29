package io.workflowai.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "io.workflowai")
@ConfigurationPropertiesScan(basePackages = "io.workflowai")
@EntityScan(basePackages = "io.workflowai.adapter.out.persistence")
@EnableJpaRepositories(basePackages = "io.workflowai.adapter.out.persistence")
public class WorkflowAIApplication {
    static void main(String[] args) {
        SpringApplication.run(WorkflowAIApplication.class, args);
    }
}
