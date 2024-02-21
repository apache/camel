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
package org.apache.camel.component.whatsapp;

import java.io.InputStream;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.webhook.WebhookConfiguration;
import org.apache.camel.component.webhook.WebhookEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class WhatsAppWebhookTest extends WhatsAppTestSupport {

    private int port;

    @Test
    public void testWebhookRegistration() throws Exception {
        String challenge = "987";
        WebhookConfiguration config
                = ((WebhookEndpoint) context().getRoute("webhook").getConsumer().getEndpoint()).getConfiguration();
        String url = config.computeFullExternalUrl() + "?hub.mode=subscribe&hub.verify_token=" + VERIFY_TOKEN
                     + "&hub.challenge=" + challenge;

        MockEndpoint mock = getMockEndpoint("mock:endpoint");
        mock.expectedBodiesReceived(challenge);
        mock.expectedMinimumMessageCount(1);

        template().sendBodyAndHeader("netty-http:" + url, null, Exchange.HTTP_METHOD, "GET");
        mock.assertIsSatisfied();
    }

    @Test
    public void testWebhookRegistrationFailed() throws Exception {
        String challenge = "987";
        WebhookConfiguration config
                = ((WebhookEndpoint) context().getRoute("webhook").getConsumer().getEndpoint()).getConfiguration();
        String url = config.computeFullExternalUrl() + "?hub.mode=subscribe&hub.verify_token=" + WRONG_VERIFY_TOKEN
                     + "&hub.challenge=" + challenge;

        MockEndpoint mock = getMockEndpoint("mock:endpoint");
        mock.expectedMinimumMessageCount(1);

        Assertions
                .assertThatThrownBy(() -> template().sendBodyAndHeader("netty-http:" + url, null, Exchange.HTTP_METHOD, "GET"))
                .rootCause()
                .hasMessageContaining("statusCode: 403");
        mock.assertIsSatisfied();
    }

    @Test
    public void testWebhook() throws Exception {
        WebhookConfiguration config
                = ((WebhookEndpoint) context().getRoute("webhook").getConsumer().getEndpoint()).getConfiguration();
        String url = config.computeFullExternalUrl();

        try (InputStream content = getClass().getClassLoader().getResourceAsStream("webhook-request.json")) {
            MockEndpoint mock = getMockEndpoint("mock:endpoint");
            mock.expectedMinimumMessageCount(1);

            template().sendBodyAndHeader("netty-http:" + url, content, Exchange.CONTENT_TYPE, "application/json");
            mock.assertIsSatisfied();
        }
        MockEndpoint mock = getMockEndpoint("mock:endpoint");

        mock.assertIsSatisfied();
        Assertions.assertThat(mock.getExchanges().get(0).getIn().getBody(String.class)).contains("\"type\": \"text\"");
    }

    @Override
    protected void doPreSetup() {
        port = AvailablePortFinder.getNextAvailable();
    }

    protected WhatsAppApiConfig getWhatsAppApiConfig() {
        return WhatsAppApiConfig.mock(port);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost").port(port);

                from("webhook:whatsapp:" + phoneNumberId + "?webhookAutoRegister=false").id("webhook")
                        .convertBodyTo(String.class).to("mock:endpoint");
            }
        };
    }

}
