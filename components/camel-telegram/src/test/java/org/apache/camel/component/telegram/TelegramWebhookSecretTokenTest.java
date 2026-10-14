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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.telegram.model.WebhookResult;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.component.telegram.util.TelegramTestUtil;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the secret token that the webhook is registered with.
 */
class TelegramWebhookSecretTokenTest extends TelegramTestSupport {

    private final List<String> setWebhookQueries = new CopyOnWriteArrayList<>();

    @Test
    void derivedSecretTokenIsStableAndUsesOnlyTheAllowedCharacters() {
        String token = TelegramEndpoint.deriveWebhookSecretToken("mock-token");

        assertEquals(token, TelegramEndpoint.deriveWebhookSecretToken("mock-token"));
        assertNotEquals(token, TelegramEndpoint.deriveWebhookSecretToken("another-token"));
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"), token);
    }

    @Test
    void invalidConfiguredSecretTokenIsRefused() {
        TelegramEndpoint endpoint = context().getEndpoint(
                "telegram:bots?authorizationToken=mock-token&webhookSecretToken=invalid.token", TelegramEndpoint.class);

        assertThrows(IllegalArgumentException.class, endpoint::resolveWebhookSecretToken);
    }

    @Test
    void registrationSendsTheDerivedSecretToken() throws Exception {
        String query = register("webhook:telegram:bots?webhookPath=/my-test&webhook-external-url=http://my-domain.com");

        assertEquals("url=http://my-domain.com/my-test&secret_token=" + TelegramEndpoint.deriveWebhookSecretToken("mock-token"),
                query);
    }

    @Test
    void registrationSendsTheConfiguredSecretToken() throws Exception {
        String query = register("webhook:telegram:bots?webhookPath=/my-test&webhook-external-url=http://my-domain.com"
                                + "&webhookSecretToken=configured_Secret-token-42");

        assertEquals("url=http://my-domain.com/my-test&secret_token=configured_Secret-token-42", query);
    }

    @Test
    void registrationSendsNoSecretTokenWhenDisabled() throws Exception {
        String query = register("webhook:telegram:bots?webhookPath=/my-test&webhook-external-url=http://my-domain.com"
                                + "&webhookSecretTokenEnabled=false");

        assertEquals("url=http://my-domain.com/my-test", query);
    }

    /**
     * Starts a webhook route against a mock Telegram API, and returns the query of the setWebhook call.
     */
    private String register(String webhookUri) throws Exception {
        WebhookResult result = new WebhookResult();
        result.setOk(true);
        result.setResult(true);
        String response = TelegramTestUtil.serialize(result);

        try (DefaultCamelContext mockContext = new DefaultCamelContext()) {
            mockContext.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    String api = "netty-http:http://localhost:" + port + "/botmock-token/";
                    from(api + "setWebhook?httpMethodRestrict=GET")
                            .process(exchange -> setWebhookQueries
                                    .add(exchange.getIn().getHeader(Exchange.HTTP_QUERY, String.class)))
                            .setBody(constant(response));
                    from(api + "deleteWebhook?httpMethodRestrict=GET")
                            .setBody(constant(response));
                }
            });
            mockContext.start();

            context().addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    restConfiguration()
                            .host("localhost")
                            .port(AvailablePortFinder.getNextAvailable());

                    from(webhookUri).to("mock:endpoint");
                }
            });
            context().start();
            await().atMost(5, TimeUnit.SECONDS).until(() -> !setWebhookQueries.isEmpty());
            context().stop();
        }
        assertEquals(1, setWebhookQueries.size());
        return setWebhookQueries.get(0);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
