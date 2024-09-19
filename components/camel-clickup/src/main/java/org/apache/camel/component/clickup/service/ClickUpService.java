package org.apache.camel.component.clickup.service;

import org.apache.camel.component.clickup.model.Webhook;

import java.util.Set;

public interface ClickUpService {

    Webhook createWebhook(Long workspaceId, String endpointUrl, Set<String> events);

    Set<Webhook> getWebhooks(Long workspaceId);

    void updateWebhook(String webhookId, String endpointUrl, Set<String> events, String status);

    void deleteWebhook(String webhookId);

}
