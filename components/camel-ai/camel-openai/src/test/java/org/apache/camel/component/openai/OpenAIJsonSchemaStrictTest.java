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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * jsonSchemaStrict sets strict to true in the JSON schema response format of chat-completion and the Responses API;
 * without it, strict is left out of the request, as before.
 */
public class OpenAIJsonSchemaStrictTest extends CamelTestSupport {

    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},"
                                         + "\"required\":[\"name\"],\"additionalProperties\":false}";

    private static final String COMPLETION = """
            {"id":"chatcmpl-1","object":"chat.completion","created":0,"model":"gpt-4o-mini",
             "choices":[{"index":0,"message":{"role":"assistant","content":"{\\"name\\":\\"camel\\"}"},
             "finish_reason":"stop"}]}
            """;

    private static final String RESPONSE = """
            {"id":"resp_1","object":"response","created_at":0,"model":"gpt-4o-mini","status":"completed",
             "output":[{"type":"message","id":"msg_1","status":"completed","role":"assistant",
               "content":[{"type":"output_text","text":"{\\"name\\":\\"camel\\"}","annotations":[]}]}],
             "parallel_tool_calls":false,"tool_choice":"auto","tools":[],"temperature":1.0,"top_p":1.0,
             "error":null,"incomplete_details":null,"instructions":null,"metadata":{}}
            """;

    private static HttpServer server;
    // the body of the last request, by path
    private static final Map<String, String> bodies = new ConcurrentHashMap<>();

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", e -> reply(e, COMPLETION));
        server.createContext("/v1/responses", e -> reply(e, RESPONSE));
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void reply(HttpExchange exchange, String json) throws IOException {
        bodies.put(exchange.getRequestURI().getPath(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        String options = "model=gpt-4o-mini&apiKey=sk-test&baseUrl=http://localhost:" + server.getAddress().getPort()
                         + "/v1&jsonSchema=" + SCHEMA;
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:chat").to("openai:chat-completion?" + options);
                from("direct:chatStrict").to("openai:chat-completion?jsonSchemaStrict=true&" + options);
                from("direct:responses").to("openai:responses?" + options);
                from("direct:responsesStrict").to("openai:responses?jsonSchemaStrict=true&" + options);
            }
        };
    }

    @Test
    void chatCompletion() {
        template.requestBody("direct:chat", "Name a camel");
        assertThat(bodies.get("/v1/chat/completions")).contains("\"type\":\"json_schema\"")
                .doesNotContain("\"strict\"");

        template.requestBody("direct:chatStrict", "Name a camel");
        assertThat(bodies.get("/v1/chat/completions")).contains("\"type\":\"json_schema\"", "\"strict\":true");
    }

    @Test
    void responses() {
        template.requestBody("direct:responses", "Name a camel");
        assertThat(bodies.get("/v1/responses")).contains("\"type\":\"json_schema\"").doesNotContain("\"strict\"");

        template.requestBody("direct:responsesStrict", "Name a camel");
        assertThat(bodies.get("/v1/responses")).contains("\"type\":\"json_schema\"", "\"strict\":true");
    }
}
