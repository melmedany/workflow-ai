package io.workflowai.adapter.in.rest.dto;

import io.workflowai.domain.agent.ChatProviderId;

import java.util.UUID;

public record AgentSummaryDto(
        UUID agentId, String displayName, boolean enabled,
        ChatProviderId chatProviderId, String model) {
}
