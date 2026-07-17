package io.workflowai.domain.model;

import java.util.UUID;

public interface AgentConfig {
    UUID id();
    String displayName();
    String description();
    boolean enabled();
    String provider();
    String model();
    double temperature();
    String systemPrompt();
    boolean memoryEnabled();
    boolean validationEnabled();
    int memoryLimit();
    PolicyConfig policyConfig();
}