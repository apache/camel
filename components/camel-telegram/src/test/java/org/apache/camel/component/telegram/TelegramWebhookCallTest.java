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

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.component.webhook.WebhookEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

/**
 * Tests a producer that sends media information.
 */
public class TelegramWebhookCallTest extends TelegramTestSupport {

    private int port;

    @Test
    public void testWebhookCall() throws Exception {
        WebhookConfiguration config = ((WebhookEndpoint) context().getRoute("webhook").getConsumer().getEndpoint()).getConfiguration();
        String url = config.computeFullExternalUrl();

        try (InputStream content = getClass().getClassLoader().getResourceAsStream("messages/webhook-call.json")) {
            MockEndpoint mock = getMockEndpoint("mock:endpoint");
            mock.expectedBodiesReceived("aho");
            mock.expectedMinimumMessageCount(1);

            template().sendBodyAndHeader("netty-http:" + url, content, Exchange.CONTENT_TYPE, "application/json");
            mock.assertIsSatisfied();
        }
    }

    @Override
    protected void doPreSetup() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration()
                        .host("localhost")
                        .port(port);

                from("webhook:telegram:bots?authorizationToken=mock-token&webhookAutoRegister=false")
                        .id("webhook")
                        .convertBodyTo(String.class)
                        .to("mock:endpoint");
            }
        };
    }
}
