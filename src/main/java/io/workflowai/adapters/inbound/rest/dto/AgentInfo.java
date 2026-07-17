package io.workflowai.adapters.inbound.rest.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record AgentInfo(UUID id, String displayName, String description, List<String> tags) implements Serializable {
}
