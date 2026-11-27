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
package org.apache.camel.component.openai;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the OpenAI direct client (chat-completion, decisions) re-resolves the OAuth token on every request
 * rather than baking it in at client-creation time. The test registers a {@link StubOAuthClientAuthenticationFactory}
 * via the service-loader mechanism and swaps the token between two requests to prove per-request resolution.
 */
class OpenAIClientOAuthTokenRefreshTest extends CamelTestSupport {

    private static final String PROFILE = "client-refresh-test";

    private static final String COMPLETION = """
            {"id":"chatcmpl-1","object":"chat.completion","created":0,"model":"stub",
             "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},
             "finish_reason":"stop"}]}
            """;

    private static HttpServer server;
    private static final List<String> recordedAuthorizations = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", OpenAIClientOAuthTokenRefreshTest::reply);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @AfterEach
    void resetStub() {
        StubOAuthClientAuthenticationFactory.reset(PROFILE);
        recordedAuthorizations.clear();
    }

    private static void reply(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        recordedAuthorizations.add(authorization != null ? authorization : "");
        byte[] body = COMPLETION.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
        exchange.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:oauth")
                        .to("openai:chat-completion?model=stub&oauthProfile=" + PROFILE + "&baseUrl=" + baseUrl);
            }
        };
    }

    /**
     * The stub returns token-1 for the first request and then token-2 for the second. The OpenAI client must re-resolve
     * the credential on every request so that an expired token is not reused.
     */
    @Test
    void tokenChangeBetweenRequestsIsPickedUp() {
        StubOAuthClientAuthenticationFactory.registerFixed(PROFILE, "token-1");

        template.requestBody("direct:oauth", "Hello", String.class);

        assertThat(recordedAuthorizations).as("first request")
                .containsExactly("Bearer token-1");

        recordedAuthorizations.clear();

        // Rotate the token — the credential supplier must be called again on the next request
        StubOAuthClientAuthenticationFactory.registerFixed(PROFILE, "token-2");

        template.requestBody("direct:oauth", "Hello again", String.class);

        assertThat(recordedAuthorizations).as("second request after token rotation")
                .containsExactly("Bearer token-2");
    }
}
