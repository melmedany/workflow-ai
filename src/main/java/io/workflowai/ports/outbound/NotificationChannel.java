package io.workflowai.ports.outbound;

import io.workflowai.domain.model.ConversationMessage;

import java.util.UUID;

/**
 * NotificationChannel is responsible for sending notifications to the users on external systems, e.g. email, WhatsApp, etc.
 * Implementation classes to be placed under io.workflowai.adapters.outbound.notifications package.
 */
public interface NotificationChannel {

    void notify(UUID agentId, UUID conversationId, ConversationMessage message);
}
