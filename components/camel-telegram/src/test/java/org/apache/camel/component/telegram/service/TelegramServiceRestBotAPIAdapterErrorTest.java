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
package org.apache.camel.component.telegram.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.telegram.TelegramException;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the errors of the Bot API requests do not show the request URI, which contains the authorization token.
 */
class TelegramServiceRestBotAPIAdapterErrorTest extends TelegramTestSupport {

    @Test
    void describeUsesTheMethodOfTheBotApi() {
        assertEquals("GET getUpdates",
                TelegramServiceRestBotAPIAdapter.describe("GET",
                        URI.create("https://api.telegram.org/bot123456:authorization-token/getUpdates?offset=1&limit=100")));
    }

    @Test
    void errorDoesNotShowTheRequestUri() {
        TelegramServiceRestBotAPIAdapter adapter = new TelegramServiceRestBotAPIAdapter(
                HttpClient.newHttpClient(), "http://localhost:" + port, "mock-token", 1024);

        TelegramException e = assertThrows(TelegramException.class,
                () -> adapter.setWebhook("http://my-domain.com/my-test", "my-secret-token"));

        assertTrue(e.getMessage().startsWith("Could not GET setWebhook: 401"), e.getMessage());
        assertFalse(e.getMessage().contains("mock-token"), e.getMessage());
        assertFalse(e.getMessage().contains("my-secret-token"), e.getMessage());
    }

    @Test
    void asyncErrorDoesNotShowTheRequestUri() {
        TelegramAsyncHandler handler = new TelegramAsyncHandler(
                "http://localhost:" + port + "/botmock-token/sendMessage", MessageResult.class, new ObjectMapper(),
                new DefaultExchange(context()), doneSync -> {
                });

        RuntimeCamelException e = assertThrows(RuntimeCamelException.class,
                () -> handler.handleCompressedResponse(response(400, "{\"ok\":false}")));

        assertEquals("POST sendMessage responded: 400{\"ok\":false}", e.getMessage());
    }

    private static HttpResponse<InputStream> response(int statusCode, String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (name, value) -> true);
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return null;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return getMockRoutes();
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
                .addErrorEndpoint("setWebhook", "GET", 401);
    }
}
