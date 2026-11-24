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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A local OpenAI-compatible server, such as Ollama, checks no key: without an apiKey (and no OPENAI_API_KEY and no
 * oauthProfile), the client is built and the request is sent without an Authorization header.
 */
public class OpenAINoApiKeyTest extends CamelTestSupport {

    private static final String COMPLETION = """
            {"id":"chatcmpl-1","object":"chat.completion","created":0,"model":"llama3.2",
             "choices":[{"index":0,"message":{"role":"assistant","content":"Hello from the local server"},
             "finish_reason":"stop"}]}
            """;

    private static HttpServer server;
    // the Authorization header of each request, or "" when it has none
    private static final List<String> authorizations = new CopyOnWriteArrayList<>();
    // the values of all the headers of the requests
    private static final List<String> headerValues = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startServer() throws IOException {
        assumeTrue(System.getenv("OPENAI_API_KEY") == null && System.getProperty("openai.api.key") == null,
                "a key from the environment would be sent");
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", OpenAINoApiKeyTest::reply);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void reply(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        authorizations.add(authorization != null ? authorization : "");
        exchange.getRequestHeaders().values().forEach(headerValues::addAll);
        byte[] body = COMPLETION.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/v1";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:noKey")
                        .to("openai:chat-completion?model=llama3.2&baseUrl=" + baseUrl);
                from("direct:key")
                        .to("openai:chat-completion?model=llama3.2&apiKey=sk-test&baseUrl=" + baseUrl);
            }
        };
    }

    @Test
    void aRequestWithoutAKeyHasNoAuthorizationHeader() {
        authorizations.clear();
        headerValues.clear();

        String answer = template.requestBody("direct:noKey", "Hello", String.class);

        assertThat(answer).isEqualTo("Hello from the local server");
        assertThat(authorizations).containsExactly("");
        // the placeholder the SDK needs is not sent in another header either
        assertThat(headerValues).noneMatch(v -> v.contains("no-api-key"));
    }

    @Test
    void aKeyIsStillSentAsABearerToken() {
        authorizations.clear();

        template.requestBody("direct:key", "Hello", String.class);

        assertThat(authorizations).containsExactly("Bearer sk-test");
    }
}
