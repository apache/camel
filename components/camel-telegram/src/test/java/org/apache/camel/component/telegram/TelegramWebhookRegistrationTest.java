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
package org.apache.camel.component.telegram;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.WebhookInfo;
import org.apache.camel.component.telegram.model.WebhookResult;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramMockRoutes.MockProcessor;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.apache.camel.impl.DefaultCamelContext;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Response;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests a producer that sends media information.
 */
public class TelegramWebhookRegistrationTest extends TelegramTestSupport {

    @Test
    public void testAutomaticRegistration() throws Exception {
        final MockProcessor<WebhookInfo> mockProcessor = getMockRoutes().getMock("setWebhook");
        mockProcessor.clearRecordedMessages();
        try (final DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            /* Make sure the Telegram mock API is up and running */
            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> {
                        final Response testResponse = Dsl.asyncHttpClient()
                                .prepareGet("http://localhost:" + port + "/botmock-token/getTest")
                                .execute().get();
                        return testResponse.getStatusCode() == 200;
                    });

            context().addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:telegram").to("telegram:bots?authorizationToken=mock-token");
                    from("webhook:telegram:bots?authorizationToken=mock-token").to("mock:endpoint");
                }
            });
            context().start();
            {
                final List<WebhookInfo> recordedMessages = mockProcessor.awaitRecordedMessages(1, 5000);
                assertEquals(1, recordedMessages.size());
                assertNotEquals("", recordedMessages.get(0).getUrl());
            }

            mockProcessor.clearRecordedMessages();
            context().stop();
            {
                final List<WebhookInfo> recordedMessages = mockProcessor.awaitRecordedMessages(1, 5000);
                assertEquals(1, recordedMessages.size());
                assertEquals("", recordedMessages.get(0).getUrl());
            }
        }
    }

    @Test
    public void testNoRegistration() throws Exception {
        final MockProcessor<WebhookInfo> mockProcessor = getMockRoutes().getMock("setWebhook");
        mockProcessor.clearRecordedMessages();
        try (final DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(getMockRoutes());
            mockContext.start();

            /* Make sure the Telegram mock API is up and running */
            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> {
                        final Response testResponse = Dsl.asyncHttpClient()
                                .prepareGet("http://localhost:" + port + "/botmock-token/getTest")
                                .execute().get();
                        return testResponse.getStatusCode() == 200;
                    });

            context().addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("webhook:telegram:bots?authorizationToken=mock-token&webhookAutoRegister=false").to("mock:endpoint");
                }
            });
            context().start();

            Awaitility.await()
                    .pollDelay(500, TimeUnit.MILLISECONDS)
                    .until(() -> mockProcessor.getRecordedMessages().size() == 0);

            context().stop();

            waitAtMost(5, TimeUnit.SECONDS).until(() -> context().getStatus() == ServiceStatus.Stopped);

            Awaitility.await()
                    .pollDelay(500, TimeUnit.MILLISECONDS)
                    .until(() -> mockProcessor.getRecordedMessages().size() == 0);

        }
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        final WebhookResult result = new WebhookResult();
        result.setOk(true);
        result.setResult(true);
        return new TelegramMockRoutes(port)
                .addEndpoint(
                        "getTest",
                        "GET",
                        String.class,
                        "running")
                .addEndpoint(
                        "setWebhook",
                        "POST",
                        WebhookInfo.class,
                        TelegramTestUtil.serialize(result));
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
