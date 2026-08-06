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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.main.Main;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Verifies MCP session idle TTL wiring from {@code camel.server.mcp-session-*} properties (CAMEL-24327).
 */
class McpServerSessionEvictionPropertiesTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05",
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    @Test
    void testSessionIdleTtlPropertyEvictsOrphanSessions() throws Exception {
        int port = AvailablePortFinder.getNextAvailable();

        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:ping?tags=crm&description=Ping tool")
                        .setBody(constant("pong"));
            }
        });
        main.addInitialProperty("camel.server.enabled", "true");
        main.addInitialProperty("camel.server.port", String.valueOf(port));
        main.addInitialProperty("camel.server.mcp-enabled", "true");
        main.addInitialProperty("camel.server.mcp-tags", "crm");
        main.addInitialProperty("camel.server.mcp-session-keep-alive-interval", "0");
        main.addInitialProperty("camel.server.mcp-session-idle-ttl", "500");
        main.start();

        try {
            initializeSession(port);
            McpServerBridge bridge = main.getCamelContext().hasService(McpServerBridge.class);
            assertThat(bridge).isNotNull();
            assertThat(bridge.getEngine()).isInstanceOf(VertxMcpServerEngine.class);
            VertxMcpServerEngine engine = (VertxMcpServerEngine) bridge.getEngine();
            assertThat(engine.sessionCount()).isEqualTo(1);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(engine.sessionCount()).isZero());
        } finally {
            main.stop();
        }
    }

    private static void initializeSession(int port) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(INITIALIZE_REQUEST))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }
}
