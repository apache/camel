package org.apache.camel.component.clickup.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.clickup.model.Webhook;
import org.apache.camel.component.clickup.model.WebhookCreationResult;
import org.apache.camel.component.clickup.model.WebhooksReadResult;
import org.apache.camel.component.clickup.model.errors.WebhookAlreadyExistsException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ClickUpServiceApiImpl implements ClickUpService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String WEBHOOK_CREATION_ERROR_WEBHOOK_ALREADY_EXISTS = "OAUTH_171";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorizationToken;

    public ClickUpServiceApiImpl(HttpClient httpClient, String baseUrl, String authorizationToken) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.authorizationToken = authorizationToken;
    }

    @Override
    public Webhook createWebhook(Long workspaceId, String endpointUrl, Set<String> events) {
        Map<String, Object> payloadKeyValuePairs = new HashMap<>();
        payloadKeyValuePairs.put("endpoint", endpointUrl);
        payloadKeyValuePairs.put("events", events);

        String payload;
        try {
            payload = MAPPER.writeValueAsString(payloadKeyValuePairs);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        var pathname = "/team/" + workspaceId + "/webhook";
        var request = HttpRequest.newBuilder()
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .uri(URI.create(this.baseUrl + pathname))
                .header("Content-Type", "application/json")
                .header("Authorization", this.authorizationToken)
                .build();

        HttpResponse<String> response;
        try {
            response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        WebhookCreationResult result;
        try {
            String body = response.body();

            result = MAPPER.readValue(body, WebhookCreationResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        if (result.isError()) {
            switch (result.getErrorCode()) {
                case WEBHOOK_CREATION_ERROR_WEBHOOK_ALREADY_EXISTS:
                    throw new WebhookAlreadyExistsException();
            }

            throw new RuntimeException("The error " + result.getErrorCode() + " has occurred during the webhook registration: " + result.getError());
        }

        return result.getWebhook();
    }

    @Override
    public Set<Webhook> getWebhooks(Long workspaceId) {
        var pathname = "/team/" + workspaceId + "/webhook";
        var request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(this.baseUrl + pathname))
                .header("Authorization", this.authorizationToken)
                .build();

        HttpResponse<String> response;
        try {
            response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        WebhooksReadResult result;
        try {
            String body = response.body();

            result = MAPPER.readValue(body, WebhooksReadResult.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return result.getWebhooks();
    }

    @Override
    public void updateWebhook(String webhookId, String endpointUrl, Set<String> events, String status) {

    }

    @Override
    public void deleteWebhook(String webhookId) {
        var pathname = "/webhook/" + webhookId;
        var request = HttpRequest.newBuilder()
                .DELETE()
                .uri(URI.create(this.baseUrl + pathname))
                .header("Authorization", this.authorizationToken)
                .build();

        try {
            this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
