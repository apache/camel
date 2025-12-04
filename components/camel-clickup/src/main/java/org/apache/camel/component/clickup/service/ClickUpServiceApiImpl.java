/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.component.clickup.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.clickup.model.Webhook;
import org.apache.camel.component.clickup.model.WebhookCreationCommand;
import org.apache.camel.component.clickup.model.WebhookCreationResult;
import org.apache.camel.component.clickup.model.WebhooksReadResult;
import org.apache.camel.component.clickup.model.errors.WebhookAlreadyExistsException;

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
    public Webhook createWebhook(Long workspaceId, WebhookCreationCommand command) {
        String payload;
        try {
            payload = MAPPER.writeValueAsString(command);
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
                default:
                    throw new RuntimeException("The error " + result.getErrorCode()
                            + " has occurred during the webhook registration: " + result.getError());
            }
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
