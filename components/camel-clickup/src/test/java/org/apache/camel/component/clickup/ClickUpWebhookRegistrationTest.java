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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickup.model.WebhookCreationCommand;
import org.apache.camel.component.clickup.util.ClickUpMockRoutes;
import org.apache.camel.component.clickup.util.ClickUpTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit5.TestExecutionConfiguration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ClickUpWebhookRegistrationTest extends ClickUpTestSupport {

    private final static Long WORKSPACE_ID = 12345L;
    private final static String AUTHORIZATION_TOKEN = "mock-authorization-token";
    private final static String WEBHOOK_SECRET = "mock-webhook-secret";
    private final static Set<String> EVENTS = new HashSet<>(List.of("taskTimeTrackedUpdated"));

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String WEBHOOK_CREATED_JSON = "messages/webhook-created.json";

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        super.configureTest(testExecutionConfiguration);

        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @Test
    public void testAutomaticRegistration() throws Exception {
        final ClickUpMockRoutes.MockProcessor<String> mockProcessor
                = getMockRoutes().getMock("POST", "team/" + WORKSPACE_ID + "/webhook");
        mockProcessor.clearRecordedMessages();

        try (final DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            waitForClickUpMockAPI();

            setupContextRoutes();

            context().start();

            final List<String> recordedMessages = mockProcessor.awaitRecordedMessages(1, 5000);
            assertEquals(1, recordedMessages.size());
            String recordedMessage = recordedMessages.get(0);

            try {
                WebhookCreationCommand command = MAPPER.readValue(recordedMessage, WebhookCreationCommand.class);

                assertInstanceOf(WebhookCreationCommand.class, command);
            } catch (IOException e) {
                fail(e);
            }

            mockProcessor.clearRecordedMessages();

            context().stop();
        }
    }

    @Test
    public void testAutomaticUnregistration() throws Exception {
        final ClickUpMockRoutes.MockProcessor<String> mockProcessor = getMockRoutes().getMock("DELETE", "webhook/");
        mockProcessor.clearRecordedMessages();

        try (final DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            waitForClickUpMockAPI();

            setupContextRoutes();

            context().start();

            context().stop();

            {
                final List<String> readRecordedMessages = mockProcessor.awaitRecordedMessages(1, 5000);
                assertEquals(1, readRecordedMessages.size());
                String webhookDeleteMessage = readRecordedMessages.get(0);

                assertEquals("", webhookDeleteMessage);

                mockProcessor.clearRecordedMessages();
            }
        }
    }

    private static void waitForClickUpMockAPI() {
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
    }

    private void setupContextRoutes() throws Exception {
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

        try (InputStream content = getClass().getClassLoader().getResourceAsStream(WEBHOOK_CREATED_JSON)) {
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
                "webhook/",
                "DELETE",
                false,
                String.class,
                () -> "{}");

        return clickUpMockRoutes;
    }
}
