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

import java.io.InputStream;
import java.util.*;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.clickup.model.TaskTimeTrackedUpdatedEvent;
import org.apache.camel.component.clickup.util.ClickUpTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.component.webhook.WebhookEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClickUpWebhookCallTest extends ClickUpTestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClickUpWebhookCallTest.class);

    private static final Long WORKSPACE_ID = 12345L;
    private static final String AUTHORIZATION_TOKEN = "mock-authorization-token";
    private static final String WEBHOOK_SECRET = "mock-webhook-secret";
    private static final Set<String> EVENTS = new HashSet<>(List.of("taskTimeTrackedUpdated"));

    public static final String MESSAGES_EVENTS_TIME_TRACKING_CREATED_FILENAME =
            "messages/events/time-tracking-created.json";
    public static final String MESSAGES_EVENTS_TIME_TRACKING_CREATED_SIGNATURE =
            "ac99f10017e28db6839941c184964890ec3262b1d6b1756d33ff53d972d5a361";

    private static int port;

    @BeforeAll
    public static void initPort() {
        port = AvailablePortFinder.getNextAvailable();
    }

    @Test
    public void testWebhookCall() throws Exception {
        WebhookConfiguration config =
                ((WebhookEndpoint) context().getRoute("webhook").getConsumer().getEndpoint()).getConfiguration();
        String url = config.computeFullExternalUrl();

        LOGGER.info("Webhook external url: {}", url);

        try (InputStream content =
                getClass().getClassLoader().getResourceAsStream(MESSAGES_EVENTS_TIME_TRACKING_CREATED_FILENAME)) {
            LOGGER.info("message content: {}", content);

            MockEndpoint mock = getMockEndpoint("mock:endpoint");
            mock.expectedMessageCount(1);
            mock.expectedMessagesMatches(exchange -> exchange.getIn().getBody() instanceof TaskTimeTrackedUpdatedEvent);

            Map<String, Object> headers = new HashMap<>();
            headers.put(Exchange.HTTP_METHOD, "POST");
            headers.put(Exchange.CONTENT_TYPE, "application/json");
            headers.put("x-signature", MESSAGES_EVENTS_TIME_TRACKING_CREATED_SIGNATURE);
            template().sendBodyAndHeaders("netty-http:" + url, content, headers);
            mock.assertIsSatisfied();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").port(port);

                from("webhook:clickup:" + WORKSPACE_ID + "?authorizationToken=" + AUTHORIZATION_TOKEN
                                + "&webhookSecret=" + WEBHOOK_SECRET + "&events=" + String.join(",", EVENTS)
                                + "&webhookAutoRegister=false")
                        .id("webhook")
                        .to("mock:endpoint");
            }
        };
    }
}
