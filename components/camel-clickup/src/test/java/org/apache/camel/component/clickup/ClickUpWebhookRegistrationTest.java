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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickup.model.WebhookCreationCommand;
import org.apache.camel.component.clickup.util.ClickUpMockRoutes;
import org.apache.camel.component.clickup.util.ClickUpTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClickUpWebhookRegistrationTest extends ClickUpTestSupport {

    private static final Set<String> EVENTS = new HashSet<>(List.of("taskTimeTrackedUpdated"));
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WEBHOOK_CREATED_JSON = "messages/webhook-created.json";

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        super.configureTest(testExecutionConfiguration);
        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @Test
    void testAutomaticRegistration() throws Exception {
        ClickUpMockRoutes.MockProcessor<String> mockProcessor
                = getMockRoutes().getMock("POST", "team/" + WORKSPACE_ID + "/webhook");
        mockProcessor.clearRecordedMessages();

        try (DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            waitForClickUpMockAPI();
            addWebhookRoute();

            context().start();

            List<String> recordedMessages = mockProcessor.awaitRecordedMessages(1, 5000);
            assertThat(recordedMessages).hasSize(1);

            WebhookCreationCommand command = MAPPER.readValue(recordedMessages.get(0), WebhookCreationCommand.class);
            assertThat(command).isNotNull();

            mockProcessor.clearRecordedMessages();
            context().stop();
        }
    }

    @Test
    void testAutomaticUnregistration() throws Exception {
        ClickUpMockRoutes.MockProcessor<String> mockProcessor = getMockRoutes().getMock("DELETE", "webhook/");
        mockProcessor.clearRecordedMessages();

        try (DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            waitForClickUpMockAPI();
            addWebhookRoute();

            context().start();
            context().stop();

            List<String> recordedMessages = mockProcessor.awaitRecordedMessages(1, 5000);
            assertThat(recordedMessages).hasSize(1);
            assertThat(recordedMessages.get(0)).isEmpty();

            mockProcessor.clearRecordedMessages();
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
