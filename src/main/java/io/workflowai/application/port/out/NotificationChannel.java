package io.workflowai.application.port.out;

import io.workflowai.domain.conversation.ConversationMessage;

import java.util.UUID;

/**
 * NotificationChannel is responsible for sending notifications to the users on external systems, e.g. email, WhatsApp, etc.
 * Implementation classes belong in io.workflowai.adapter.out.notification.
 */
public interface NotificationChannel {

    void notify(UUID agentId, UUID conversationId, ConversationMessage message);
}
