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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.mcp.server.McpServerTool;
import org.apache.camel.component.mcp.server.McpToolCallHandler;
import org.apache.camel.component.mcp.server.McpToolCallResult;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Vert.x streamable transport checks the {@code Host} header on every method of the MCP endpoint when
 * allowed hosts are configured, and not at all otherwise (CAMEL-26665).
 */
class VertxMcpHostTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    private static final String INITIALIZED_NOTIFICATION = """
            {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""";

    private static final String CALL_TOOL_REQUEST = """
            {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"ping","arguments":{}}}""";

    private CamelContext camelContext;
    private VertxMcpServerEngine engine;
    private int port;

    @AfterEach
    void stop() {
        if (engine != null) {
            engine.stop();
        }
        if (camelContext != null) {
            camelContext.stop();
        }
    }

    @Test
    void hostIsNotCheckedByDefault() throws Exception {
        start(null);

        assertThat(post("localhost", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
        assertThat(post("127.0.0.1", null, INITIALIZE_REQUEST).statusCode()).isEqualTo(200);
        assertThat(rawPost("Host: attacker.example.com:" + port)).startsWith("HTTP/1.1 200");
        assertThat(engine.sessionCount()).isEqualTo(3);
    }

    @Test
    void allowedHostIsServed() throws Exception {
        start(List.of("localhost:*"));

        HttpResponse<String> response = post("localhost", null, INITIALIZE_REQUEST);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("mcp-session-id")).isPresent();
        assertThat(engine.sessionCount()).isEqualTo(1);
    }

    @Test
    void otherHostIsRejected() throws Exception {
        start(List.of("localhost:*"));

        HttpResponse<String> response = post("127.0.0.1", null, INITIALIZE_REQUEST);

        assertThat(response.statusCode()).isEqualTo(421);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        assertThat(response.body())
                .contains("\"jsonrpc\":\"2.0\"")
                .contains("\"code\":-32600")
                .contains("Host not allowed")
                .doesNotContain("\"id\"");
        assertThat(response.headers().firstValue("mcp-session-id")).isEmpty();
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void reboundHostNameIsRejected() throws Exception {
        start(List.of("localhost:*", "127.0.0.1:*"));

        // a page on a host name that resolves to this server (DNS rebinding) sends its own host name
        assertThat(rawPost("Host: attacker.example.com:" + port)).startsWith("HTTP/1.1 421");
        assertThat(rawPost("Host: localhost.attacker.example.com:" + port)).startsWith("HTTP/1.1 421");
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void missingHostIsRejected() throws Exception {
        start(List.of("localhost:*"));

        // Vert.x refuses an HTTP/1.1 request without a Host header itself; HTTP/1.0 has no Host header to require
        assertThat(rawPost("HTTP/1.1", null)).startsWith("HTTP/1.1 400");
        assertThat(rawPost("HTTP/1.0", null)).contains(" 421 ");
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void hostIsComparedIgnoringCase() throws Exception {
        start(List.of("mcp.example.com:*"));

        assertThat(rawPost("Host: MCP.Example.COM:" + port)).startsWith("HTTP/1.1 200");
    }

    @Test
    void wildcardPortAlsoMatchesAHostWithoutAPort() throws Exception {
        start(List.of("mcp.example.com:*"));

        assertThat(rawPost("Host: mcp.example.com")).startsWith("HTTP/1.1 200");
        assertThat(rawPost("Host: mcp.example.com:" + port)).startsWith("HTTP/1.1 200");
    }

    @Test
    void exactPortMatchesThatPortOnly() throws Exception {
        start(List.of("mcp.example.com:8443", "proxy.example.com"));

        assertThat(rawPost("Host: mcp.example.com:8443")).startsWith("HTTP/1.1 200");
        assertThat(rawPost("Host: mcp.example.com:" + port)).startsWith("HTTP/1.1 421");
        assertThat(rawPost("Host: mcp.example.com")).startsWith("HTTP/1.1 421");
        assertThat(rawPost("Host: proxy.example.com")).startsWith("HTTP/1.1 200");
        assertThat(rawPost("Host: proxy.example.com:" + port)).startsWith("HTTP/1.1 421");
    }

    @Test
    void originIsStillCheckedFirst() throws Exception {
        start(List.of("localhost:*"));

        HttpResponse<String> response = post("localhost", "http://app.example.com", null, INITIALIZE_REQUEST);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("Origin not allowed");
    }

    @Test
    void rejectedHostDoesNotCallTheTool() throws Exception {
        start(List.of("localhost:*"));
        AtomicInteger calls = new AtomicInteger();
        engine.toolAdded(tool("ping", arguments -> {
            calls.incrementAndGet();
            return new McpToolCallResult("pong", false);
        }));
        String sessionId = initializeSession();

        HttpResponse<String> rejected = post("127.0.0.1", sessionId, CALL_TOOL_REQUEST);
        assertThat(rejected.statusCode()).isEqualTo(421);
        assertThat(calls).hasValue(0);

        HttpResponse<String> served = post("localhost", sessionId, CALL_TOOL_REQUEST);
        assertThat(served.statusCode()).isEqualTo(200);
        assertThat(served.body()).contains("pong");
        assertThat(calls).hasValue(1);
    }

    @Test
    void sseChannelChecksTheHost() throws Exception {
        start(List.of("localhost:*"));
        String sessionId = initializeSession();

        HttpResponse<InputStream> rejected = HTTP.send(
                request("127.0.0.1", null, sessionId).header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = rejected.body()) {
            assertThat(rejected.statusCode()).isEqualTo(421);
        }
    }

    @Test
    void deleteChecksTheHost() throws Exception {
        start(List.of("localhost:*"));
        String sessionId = initializeSession();

        assertThat(delete("127.0.0.1", sessionId)).isEqualTo(421);
        assertThat(engine.sessionCount()).isEqualTo(1);

        assertThat(delete("localhost", sessionId)).isEqualTo(200);
        assertThat(engine.sessionCount()).isZero();
    }

    private void start(List<String> allowedHosts) throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        camelContext = new DefaultCamelContext();
        MainHttpServer main = new MainHttpServer();
        main.setCamelContext(camelContext);
        main.setHost("0.0.0.0");
        main.setPort(port);
        camelContext.addService(main);
        camelContext.start();

        engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.initialize(new McpServerInfo(
                "host", "1.0", "/mcp", 0, 0, null, null, null, null, null, null, null, null, null, allowedHosts));
        engine.start();
    }

    private String initializeSession() throws Exception {
        HttpResponse<String> response = post("localhost", null, INITIALIZE_REQUEST);
        assertThat(response.statusCode()).isEqualTo(200);
        String sessionId = response.headers().firstValue("mcp-session-id").orElseThrow();
        assertThat(post("localhost", sessionId, INITIALIZED_NOTIFICATION).statusCode()).isEqualTo(202);
        return sessionId;
    }

    private HttpResponse<String> post(String host, String origin, String sessionId, String body) throws Exception {
        HttpRequest.Builder builder = request(host, origin, sessionId)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String host, String sessionId, String body) throws Exception {
        return post(host, null, sessionId, body);
    }

    private int delete(String host, String sessionId) throws Exception {
        HttpRequest.Builder builder = request(host, null, sessionId).DELETE();
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    /**
     * A request to {@code host} on the port of the server: the HTTP client sends the host and port of the URI as the
     * {@code Host} header, and both {@code localhost} and {@code 127.0.0.1} reach the server.
     */
    private HttpRequest.Builder request(String host, String origin, String sessionId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://" + host + ":" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10));
        if (origin != null) {
            builder.header("Origin", origin);
        }
        if (sessionId != null) {
            builder.header("Mcp-Session-Id", sessionId);
        }
        return builder;
    }

    /**
     * Sends an initialize request over a plain socket, with the given HTTP version and {@code Host} header line, or
     * none when {@code null} (the HTTP client does not allow setting it), and returns the response.
     */
    private String rawPost(String hostLine) throws Exception {
        return rawPost("HTTP/1.1", hostLine);
    }

    private String rawPost(String version, String hostLine) throws Exception {
        byte[] body = INITIALIZE_REQUEST.getBytes(StandardCharsets.UTF_8);
        String head = "POST /mcp " + version + "\r\n"
                      + (hostLine != null ? hostLine + "\r\n" : "")
                      + "Content-Type: application/json\r\n"
                      + "Accept: application/json, text/event-stream\r\n"
                      + "Content-Length: " + body.length + "\r\n"
                      + "Connection: close\r\n\r\n";
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static McpServerTool tool(String name, McpToolCallHandler handler) {
        return new McpServerTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "Answers pong";
            }

            @Override
            public String inputSchemaJson() {
                return null;
            }

            @Override
            public Map<String, ParameterDef> parameters() {
                return Map.of();
            }

            @Override
            public McpToolCallHandler handler() {
                return handler;
            }
        };
    }
}
