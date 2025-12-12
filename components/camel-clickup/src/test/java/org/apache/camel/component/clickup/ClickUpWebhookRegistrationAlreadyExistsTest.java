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
package org.apache.camel.component.clickup;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickup.model.Webhook;
import org.apache.camel.component.clickup.model.WebhookCreationCommand;
import org.apache.camel.component.clickup.model.WebhooksReadResult;
import org.apache.camel.component.clickup.util.ClickUpMockRoutes;
import org.apache.camel.component.clickup.util.ClickUpTestSupport;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.component.webhook.WebhookEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClickUpWebhookRegistrationAlreadyExistsTest extends ClickUpTestSupport {

    private final static Long WORKSPACE_ID = 12345L;
    private final static String AUTHORIZATION_TOKEN = "mock-authorization-token";
    private final static String WEBHOOK_SECRET = "mock-webhook-secret";
    private final static Set<String> EVENTS = new HashSet<>(List.of("taskTimeTrackedUpdated"));

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String WEBHOOK_ALREADY_EXISTS_JSON = "messages/webhook-already-exists.json";
    public static final String WEBHOOKS = "messages/webhooks.json";

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        super.configureTest(testExecutionConfiguration);

        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @Test
    public void testAutomaticRegistrationWhenWebhookConfigurationAlreadyExists() throws Exception {
        final ClickUpMockRoutes.MockProcessor<String> creationMockProcessor
                = getMockRoutes().getMock("POST", "team/" + WORKSPACE_ID + "/webhook");
        creationMockProcessor.clearRecordedMessages();

        final ClickUpMockRoutes.MockProcessor<String> readMockProcessor
                = getMockRoutes().getMock("GET", "team/" + WORKSPACE_ID + "/webhook");
        readMockProcessor.clearRecordedMessages();

        try (final DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            /* Make sure the ClickUp mock API is up and running */
            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> {
                        HttpClient client = HttpClient.newBuilder().build();
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/clickup-api-mock/health")).GET().build();

                        final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        return response.statusCode() == 200;
                    });

            context().addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    String apiMockBaseUrl = "http://localhost:" + port + "/clickup-api-mock";

                    from("webhook:clickup:" + WORKSPACE_ID + "?authorizationToken=" + AUTHORIZATION_TOKEN + "&webhookSecret="
                         + WEBHOOK_SECRET + "&events=" + String.join(",", EVENTS) + "&webhookAutoRegister=true&baseUrl="
                         + apiMockBaseUrl)
                            .id("webhook")
                            .to("mock:endpoint");
                }
            });

            context().start();

            {
                final List<String> creationRecordedMessages = creationMockProcessor.awaitRecordedMessages(1, 5000);
                assertEquals(1, creationRecordedMessages.size());
                String webhookCreationMessage = creationRecordedMessages.get(0);

                try {
                    WebhookCreationCommand command = MAPPER.readValue(webhookCreationMessage, WebhookCreationCommand.class);

                    assertInstanceOf(WebhookCreationCommand.class, command);
                } catch (IOException e) {
                    fail(e);
                }

                creationMockProcessor.clearRecordedMessages();
            }

            {
                final List<String> readRecordedMessages = readMockProcessor.awaitRecordedMessages(1, 5000);
                assertEquals(1, readRecordedMessages.size());
                String webhookReadMessage = readRecordedMessages.get(0);

                assertEquals("", webhookReadMessage);

                readMockProcessor.clearRecordedMessages();
            }

            context().stop();
        }
    }

    @Override
    protected ClickUpMockRoutes createMockRoutes() {
        ClickUpMockRoutes clickUpMockRoutes = new ClickUpMockRoutes(port);

        clickUpMockRoutes.addEndpoint(
                "health",
                "GET",
                true,
                String.class,
                () -> "");

        try (InputStream content = getClass().getClassLoader().getResourceAsStream(WEBHOOK_ALREADY_EXISTS_JSON)) {
            assert content != null;

            String responseBody = new String(content.readAllBytes());

            clickUpMockRoutes.addEndpoint(
                    "team/" + WORKSPACE_ID + "/webhook",
                    "POST",
                    true,
                    String.class,
                    () -> responseBody);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        clickUpMockRoutes.addEndpoint(
                "team/" + WORKSPACE_ID + "/webhook",
                "GET",
                true,
                String.class,
                () -> {
                    String webhookExternalUrl;
                    try {
                        Optional<Endpoint> optionalEndpoint = context().getEndpoints().stream()
                                .filter(endpoint -> endpoint instanceof WebhookEndpoint)
                                .findFirst();

                        if (optionalEndpoint.isEmpty()) {
                            throw new RuntimeException("Could not find clickup webhook endpoint. This should never happen.");
                        }

                        WebhookEndpoint webhookEndpoint = (WebhookEndpoint) (optionalEndpoint.get());

                        WebhookConfiguration config = webhookEndpoint.getConfiguration();
                        webhookExternalUrl = config.computeFullExternalUrl();
                    } catch (UnknownHostException e) {
                        throw new RuntimeException(e);
                    }

                    WebhooksReadResult webhooksReadResult = getJSONResource(WEBHOOKS, WebhooksReadResult.class);
                    Optional<Webhook> webhook = webhooksReadResult.getWebhooks().stream().findFirst();
                    if (webhook.isEmpty()) {
                        throw new RuntimeException(
                                "Could not find the testing webhook. This should never happen, since its reading webhooks from a static file.");
                    }
                    webhook.get().setEndpoint(webhookExternalUrl);

                    String readWebhooksResponseBody;
                    try {
                        readWebhooksResponseBody = MAPPER.writeValueAsString(webhooksReadResult);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    return readWebhooksResponseBody;
                });

        return clickUpMockRoutes;
    }
}
