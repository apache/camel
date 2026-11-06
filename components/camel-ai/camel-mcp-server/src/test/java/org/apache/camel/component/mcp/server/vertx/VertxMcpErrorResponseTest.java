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
package org.apache.camel.component.mcp.server.vertx;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import io.modelcontextprotocol.spec.McpSchema;
import org.apache.camel.CamelContext;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error replies of the streamable HTTP transport on {@code /mcp} are JSON-RPC error responses without an id.
 */
class VertxMcpErrorResponseTest {

    private static final String ACCEPT = "application/json, text/event-stream";
    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private static CamelContext camelContext;
    private static VertxMcpServerEngine engine;
    private static String url;

    @BeforeAll
    static void startServer() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();
        camelContext = new DefaultCamelContext();
        MainHttpServer main = new MainHttpServer();
        main.setCamelContext(camelContext);
        main.setHost("0.0.0.0");
        main.setPort(port);
        camelContext.addService(main);
        camelContext.start();

        engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.initialize(new McpServerInfo("error-responses", "1.0", "/mcp"));
        engine.start();

        url = "http://localhost:" + port + "/mcp";
    }

    @AfterAll
    static void stopServer() {
        engine.stop();
        camelContext.stop();
    }

    @Test
    void wrongContentType() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "text/plain")
                .header("Accept", ACCEPT)
                .POST(HttpRequest.BodyPublishers.ofString(PING)));

        assertThat(response.statusCode()).isEqualTo(415);
        assertJsonRpcError(response, McpSchema.ErrorCodes.INVALID_REQUEST, "Content-Type application/json required");
    }

    @Test
    void invalidMessage() throws Exception {
        HttpResponse<String> response = send(post("{\"jsonrpc\":", null));

        assertThat(response.statusCode()).isEqualTo(400);
        assertJsonRpcError(response, McpSchema.ErrorCodes.PARSE_ERROR, "Invalid message format");
    }

    @Test
    void missingSessionId() throws Exception {
        HttpResponse<String> response = send(post(PING, null));

        assertThat(response.statusCode()).isEqualTo(400);
        assertJsonRpcError(response, McpSchema.ErrorCodes.INVALID_REQUEST, "Session ID required in Mcp-Session-Id header");
    }

    @Test
    void unknownSession() throws Exception {
        HttpResponse<String> response = send(post(PING, "no-such-session"));

        assertThat(response.statusCode()).isEqualTo(404);
        assertJsonRpcError(response, McpSchema.ErrorCodes.INVALID_REQUEST, "Session not found");
    }

    @Test
    void deleteWithoutSessionId() throws Exception {
        HttpResponse<String> response = send(HttpRequest.newBuilder(URI.create(url)).DELETE());

        assertThat(response.statusCode()).isEqualTo(400);
        assertJsonRpcError(response, McpSchema.ErrorCodes.INVALID_REQUEST, "Session ID required in Mcp-Session-Id header");
    }

    private static HttpRequest.Builder post(String body, String sessionId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", ACCEPT)
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return builder;
    }

    private static HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return HTTP.send(builder.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertJsonRpcError(HttpResponse<String> response, int code, String message) throws Exception {
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        JsonObject json = (JsonObject) Jsoner.deserialize(response.body());
        assertThat(json).containsOnlyKeys("jsonrpc", "error");
        assertThat(json.getString("jsonrpc")).isEqualTo("2.0");
        JsonObject error = json.getMap("error");
        assertThat(error).containsOnlyKeys("code", "message");
        assertThat(error.getInteger("code")).isEqualTo(code);
        assertThat(error.getString("message")).isEqualTo(message);
    }
}
