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
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickUpWebhookRegistrationAlreadyExistsTest extends ClickUpTestSupport {

    private static final Set<String> EVENTS = new HashSet<>(List.of("taskTimeTrackedUpdated"));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WEBHOOK_ALREADY_EXISTS_JSON = "messages/webhook-already-exists.json";
    private static final String WEBHOOKS = "messages/webhooks.json";

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        super.configureTest(testExecutionConfiguration);
        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @Test
    void testAutomaticRegistrationWhenWebhookConfigurationAlreadyExists() throws Exception {
        ClickUpMockRoutes.MockProcessor<String> creationMockProcessor
                = getMockRoutes().getMock("POST", "team/" + WORKSPACE_ID + "/webhook");
        creationMockProcessor.clearRecordedMessages();

        ClickUpMockRoutes.MockProcessor<String> readMockProcessor
                = getMockRoutes().getMock("GET", "team/" + WORKSPACE_ID + "/webhook");
        readMockProcessor.clearRecordedMessages();

        try (DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            waitForClickUpMockAPI();
            addWebhookRoute();

            context().start();

            List<String> creationRecordedMessages = creationMockProcessor.awaitRecordedMessages(1, 5000);
            assertThat(creationRecordedMessages).hasSize(1);

            WebhookCreationCommand command = MAPPER.readValue(creationRecordedMessages.get(0), WebhookCreationCommand.class);
            assertThat(command).isNotNull();
            creationMockProcessor.clearRecordedMessages();

            List<String> readRecordedMessages = readMockProcessor.awaitRecordedMessages(1, 5000);
            assertThat(readRecordedMessages).hasSize(1);
            assertThat(readRecordedMessages.get(0)).isEmpty();
            readMockProcessor.clearRecordedMessages();

            context().stop();
        }
    }

    private void addWebhookRoute() throws Exception {
        context().addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                String apiMockBaseUrl = "http://localhost:" + port + "/clickup-api-mock";

                fromF("webhook:clickup:%s?authorizationToken=%s&webhookSecret=%s&events=%s&webhookAutoRegister=true&baseUrl=%s",
                        WORKSPACE_ID, AUTHORIZATION_TOKEN, WEBHOOK_SECRET, String.join(",", EVENTS), apiMockBaseUrl)
                        .id("webhook")
                        .to("mock:endpoint");
            }
        });
    }

    @Override
    protected ClickUpMockRoutes createMockRoutes() {
        ClickUpMockRoutes clickUpMockRoutes = new ClickUpMockRoutes(port.getPort());

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
