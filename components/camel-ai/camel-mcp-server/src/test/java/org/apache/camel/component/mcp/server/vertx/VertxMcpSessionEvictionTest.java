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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.mcp.server.McpServerInfo;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.AvailablePortFinder.Port;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies active MCP session eviction on the Vert.x streamable transport (CAMEL-24327).
 */
class VertxMcpSessionEvictionTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05",
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    private static final String INITIALIZED_NOTIFICATION = """
            {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}""";

    @Test
    void evictsIdleSessionWithoutDelete() throws Exception {
        try (Port portHolder = AvailablePortFinder.find()) {
            int port = portHolder.getPort();
            VertxMcpServerEngine engine = startEngine(port, new McpServerInfo("eviction", "1.0", "/mcp", 0, 500));
            try {
                initializeSession(port);
                assertThat(engine.sessionCount()).isEqualTo(1);

                await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(engine.sessionCount()).isZero());
            } finally {
                engine.stop();
            }
        }
    }

    @Test
    void postOnlySessionSurvivesKeepAliveUntilIdle() throws Exception {
        try (Port portHolder = AvailablePortFinder.find()) {
            int port = portHolder.getPort();
            // keep-alive interval 300 ms, idle TTL 10 s — session must stay alive while POSTs keep arriving
            VertxMcpServerEngine engine = startEngine(port, new McpServerInfo("eviction", "1.0", "/mcp", 300, 10_000));
            try {
                String sessionId = initializeSession(port);

                await().during(500, TimeUnit.MILLISECONDS).atMost(1500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
                    assertThat(postWithSession(port, sessionId, INITIALIZED_NOTIFICATION)).isEqualTo(202);
                    assertThat(engine.sessionCount()).isEqualTo(1);
                });
            } finally {
                engine.stop();
            }
        }
    }

    @Test
    void activeSessionSurvivesIdleEviction() throws Exception {
        try (Port portHolder = AvailablePortFinder.find()) {
            int port = portHolder.getPort();
            // TTL 300 ms so each POST must call touch() or the session would be evicted between polls
            VertxMcpServerEngine engine = startEngine(port, new McpServerInfo("eviction", "1.0", "/mcp", 0, 300));
            try {
                String sessionId = initializeSession(port);

                await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
                    assertThat(postWithSession(port, sessionId, INITIALIZED_NOTIFICATION)).isEqualTo(202);
                    assertThat(engine.sessionCount()).isEqualTo(1);
                });
            } finally {
                engine.stop();
            }
        }
    }

    @Test
    void explicitDeleteRemovesSessionImmediately() throws Exception {
        try (Port portHolder = AvailablePortFinder.find()) {
            int port = portHolder.getPort();
            VertxMcpServerEngine engine = startEngine(port, new McpServerInfo("eviction", "1.0", "/mcp", 0, 60_000));
            try {
                String sessionId = initializeSession(port);
                assertThat(deleteSession(port, sessionId)).isEqualTo(200);
                assertThat(engine.sessionCount()).isZero();
            } finally {
                engine.stop();
            }
        }
    }

    private static VertxMcpServerEngine startEngine(int port, McpServerInfo info) throws Exception {
        CamelContext camelContext = new DefaultCamelContext();
        MainHttpServer main = new MainHttpServer();
        main.setCamelContext(camelContext);
        main.setHost("0.0.0.0");
        main.setPort(port);
        camelContext.addService(main);
        camelContext.start();

        VertxMcpServerEngine engine = new VertxMcpServerEngine();
        engine.setCamelContext(camelContext);
        engine.initialize(info);
        engine.start();
        return engine;
    }

    private static String initializeSession(int port) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_REQUEST))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue("mcp-session-id").orElseThrow();
    }

    private static int postWithSession(int port, String sessionId, String body) throws Exception {
        HttpResponse<Void> response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Mcp-Session-Id", sessionId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }

    private static int deleteSession(int port, String sessionId) throws Exception {
        HttpResponse<Void> response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Mcp-Session-Id", sessionId)
                .DELETE()
                .build(), HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }
}
