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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.camel.CamelContext;
import org.apache.camel.component.ai.tool.AiToolParameterHelper.ParameterDef;
import org.apache.camel.component.mcp.server.McpProtocolEra;
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
import static org.awaitility.Awaitility.await;

/**
 * Verifies, over plain HTTP, what the MCP endpoint serves for each protocol era and with the stateless option
 * (CAMEL-27145): by default both eras, chosen from the body of each request, clients that open with {@code initialize}
 * with a session and clients of protocol version 2026-07-28 without one; the legacy era behaves as before Camel 4.24;
 * the stateless option serves the clients that open with {@code initialize} without a session too.
 */
class VertxMcpProtocolEraTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private static final String MODERN = "2026-07-28";

    private static final String INITIALIZE_REQUEST = """
            {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25",
            "capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""";

    private static final String LEGACY_CALL_TOOL_REQUEST = """
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
    void legacyEraServesOnlyLegacyVersionsWithSessions() throws Exception {
        start(McpProtocolEra.LEGACY);

        HttpResponse<String> initialize = post(INITIALIZE_REQUEST, Map.of());
        assertThat(initialize.statusCode()).isEqualTo(200);
        assertThat(initialize.headers().firstValue("mcp-session-id")).isPresent();
        assertThat(engine.sessionCount()).isEqualTo(1);

        // a request of protocol version 2026-07-28 has no session: answered as before Camel 4.24, a session is required
        HttpResponse<String> modern = post(modernRequest(2, "tools/list", "{}"), modernHeaders("tools/list", null));
        assertThat(modern.statusCode()).isEqualTo(400);
        assertThat(modern.body()).contains("Session ID required");
    }

    @Test
    void dualIsTheDefault() throws Exception {
        start(null);

        HttpResponse<String> initialize = post(INITIALIZE_REQUEST, Map.of());
        assertThat(initialize.statusCode()).isEqualTo(200);
        assertThat(initialize.headers().firstValue("mcp-session-id")).isPresent();

        HttpResponse<String> call = post(modernRequest(2, "tools/call", "{\"name\":\"ping\",\"arguments\":{}}"),
                modernHeaders("tools/call", "ping"));
        assertThat(call.statusCode()).isEqualTo(200);
        assertThat(call.body()).contains("pong");
        assertThat(call.headers().firstValue("mcp-session-id")).isEmpty();
        assertThat(engine.sessionCount()).isEqualTo(1);
    }

    @Test
    void statelessServesLegacyVersionsWithoutSession() throws Exception {
        start(McpProtocolEra.DUAL, true);

        HttpResponse<String> initialize = post(INITIALIZE_REQUEST, Map.of());
        assertThat(initialize.statusCode()).isEqualTo(200);
        assertThat(initialize.headers().firstValue("mcp-session-id")).isEmpty();
        // no session, so no notification: the tool list does not advertise listChanged
        assertThat(initialize.body()).contains("\"tools\":{\"listChanged\":false}");

        // any request is served on its own, without initialize before it
        HttpResponse<String> call = post(LEGACY_CALL_TOOL_REQUEST, Map.of());
        assertThat(call.statusCode()).isEqualTo(200);
        assertThat(call.headers().firstValue("Content-Type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/json"));
        assertThat(call.body()).contains("pong").contains("\"id\":2");

        // the clients of 2026-07-28 are served as always
        HttpResponse<String> modern = post(modernRequest(3, "tools/call", "{\"name\":\"ping\",\"arguments\":{}}"),
                modernHeaders("tools/call", "ping"));
        assertThat(modern.statusCode()).isEqualTo(200);
        assertThat(modern.body()).contains("pong");

        assertThat(get().statusCode()).isEqualTo(405);
        assertThat(get().headers().firstValue("Allow")).hasValue("POST");
        assertThat(delete()).isEqualTo(405);
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void statelessLegacyEraRejectsModernRequest() throws Exception {
        start(McpProtocolEra.LEGACY, true);

        HttpResponse<String> response = post(modernRequest(7, "server/discover", "{}"),
                modernHeaders("server/discover", null));

        // as the legacy era with sessions: not an error of 2026-07-28, which would identify a server of that version,
        // so a client that also speaks a legacy version falls back to initialize
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body())
                .contains("\"code\":-32600")
                .contains("Protocol version 2026-07-28 is not served")
                .contains("2025-03-26, 2025-06-18, 2025-11-25")
                .doesNotContain("-32022")
                .doesNotContain("2024-11-05");

        // the transport answers a notification it cannot accept with an HTTP error status, too
        String notification = modernRequest(0, "notifications/cancelled", "{\"requestId\":1}")
                .replace("\"id\":0,", "");
        HttpResponse<String> rejected = post(notification, modernHeaders("notifications/cancelled", null));
        assertThat(rejected.statusCode()).isEqualTo(400);
        assertThat(rejected.body()).contains("\"code\":-32600").doesNotContain("\"id\"");
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void dualAcceptsANotificationOfTheModernVersion() throws Exception {
        start(McpProtocolEra.DUAL);

        String notification = modernRequest(0, "notifications/cancelled", "{\"requestId\":1}")
                .replace("\"id\":0,", "");
        HttpResponse<String> response = post(notification, modernHeaders("notifications/cancelled", null));

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void dualServesBothErasWithOneSessionForTheLegacyClient() throws Exception {
        start(McpProtocolEra.DUAL);

        HttpResponse<String> initialize = post(INITIALIZE_REQUEST, Map.of());
        assertThat(initialize.statusCode()).isEqualTo(200);
        String sessionId = initialize.headers().firstValue("mcp-session-id").orElseThrow();

        HttpResponse<String> list = post(modernRequest(2, "tools/list", "{}"), modernHeaders("tools/list", null));
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains("\"name\":\"ping\"").contains("\"resultType\":\"complete\"");
        assertThat(list.headers().firstValue("mcp-session-id")).isEmpty();

        HttpResponse<String> call = post(modernRequest(3, "tools/call", "{\"name\":\"ping\",\"arguments\":{}}"),
                modernHeaders("tools/call", "ping"));
        assertThat(call.statusCode()).isEqualTo(200);
        assertThat(call.body()).contains("pong").contains("\"id\":3");

        // a modern request carrying an old session id is served statelessly too
        Map<String, String> withSession = Map.of("Mcp-Session-Id", sessionId, "MCP-Protocol-Version", MODERN,
                "Mcp-Method", "tools/list");
        assertThat(post(modernRequest(4, "tools/list", "{}"), withSession).statusCode()).isEqualTo(200);

        // the legacy session keeps working next to them
        HttpResponse<String> legacyCall = post(LEGACY_CALL_TOOL_REQUEST, Map.of("Mcp-Session-Id", sessionId));
        assertThat(legacyCall.statusCode()).isEqualTo(200);
        assertThat(legacyCall.body()).contains("pong");
        assertThat(engine.sessionCount()).isEqualTo(1);
    }

    @Test
    void dualRejectsHeadersThatDisagreeWithTheBody() throws Exception {
        start(McpProtocolEra.DUAL);

        // the version header names a legacy version while the body names 2026-07-28
        HttpResponse<String> version = post(modernRequest(2, "tools/list", "{}"),
                Map.of("MCP-Protocol-Version", "2025-11-25", "Mcp-Method", "tools/list"));
        assertThat(version.statusCode()).isEqualTo(400);
        assertThat(version.body()).contains("\"code\":-32020");

        HttpResponse<String> method = post(modernRequest(3, "tools/list", "{}"), modernHeaders("prompts/list", null));
        assertThat(method.statusCode()).isEqualTo(400);
        assertThat(method.body()).contains("\"code\":-32020").contains("Mcp-Method");

        HttpResponse<String> name = post(modernRequest(4, "tools/call", "{\"name\":\"ping\",\"arguments\":{}}"),
                modernHeaders("tools/call", "other"));
        assertThat(name.statusCode()).isEqualTo(400);
        assertThat(name.body()).contains("\"code\":-32020").contains("Mcp-Name");

        HttpResponse<String> missing = post(modernRequest(5, "tools/list", "{}"), Map.of("Mcp-Method", "tools/list"));
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(missing.body()).contains("\"code\":-32020").contains("MCP-Protocol-Version");
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void dualDecidesFromTheBodyWhenNoMcpHeaderIsSent() throws Exception {
        start(McpProtocolEra.DUAL);

        // no MCP header and no _meta: a client of a protocol version that opens with initialize, served as before
        HttpResponse<String> legacy = post(LEGACY_CALL_TOOL_REQUEST, Map.of());
        assertThat(legacy.statusCode()).isEqualTo(400);
        assertThat(legacy.body()).contains("Session ID required");

        // the body names 2026-07-28: served by that protocol, which requires its headers, never as a legacy request
        HttpResponse<String> modern = post(modernRequest(2, "tools/call", "{\"name\":\"ping\",\"arguments\":{}}"),
                Map.of());
        assertThat(modern.statusCode()).isEqualTo(400);
        assertThat(modern.body()).contains("\"code\":-32020").contains("Missing required header: Mcp-Method");

        // the headers name 2026-07-28 but the body does not: rejected for the missing _meta
        HttpResponse<String> noMeta = post(LEGACY_CALL_TOOL_REQUEST, modernHeaders("tools/call", "ping"));
        assertThat(noMeta.statusCode()).isEqualTo(400);
        assertThat(noMeta.body()).contains("\"code\":-32602").contains("_meta");
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void dualAnswersAnUnknownVersionWithTheSupportedOnes() throws Exception {
        start(McpProtocolEra.DUAL);

        String body = modernRequest(2, "tools/list", "{}").replace(MODERN, "2099-01-01");
        HttpResponse<String> response = post(body,
                Map.of("MCP-Protocol-Version", "2099-01-01", "Mcp-Method", "tools/list"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":-32022").contains("2026-07-28");
    }

    @Test
    void modernEraRejectsInitializeAndServesWithoutSessions() throws Exception {
        start(McpProtocolEra.MODERN);

        HttpResponse<String> initialize = post(INITIALIZE_REQUEST, Map.of());
        assertThat(initialize.statusCode()).isEqualTo(400);
        assertThat(initialize.body()).contains("\"code\":-32022").contains("2026-07-28");
        assertThat(initialize.headers().firstValue("mcp-session-id")).isEmpty();

        HttpResponse<String> discover = post(modernRequest(2, "server/discover", "{}"),
                modernHeaders("server/discover", null));
        assertThat(discover.statusCode()).isEqualTo(200);
        assertThat(discover.body())
                .contains("\"name\":\"eras\"")
                .contains("Use the ping tool")
                .contains("\"supportedVersions\":[\"2026-07-28\"]");

        HttpResponse<String> call = post(modernRequest(3, "tools/call", "{\"name\":\"ping\",\"arguments\":{}}"),
                modernHeaders("tools/call", "ping"));
        assertThat(call.statusCode()).isEqualTo(200);
        assertThat(call.body()).contains("pong");

        assertThat(get().statusCode()).isEqualTo(405);
        assertThat(delete()).isEqualTo(405);
        assertThat(engine.sessionCount()).isZero();
    }

    @Test
    void listenStreamIsToldWhenAToolIsAdded() throws Exception {
        start(McpProtocolEra.DUAL);

        String listen = modernRequest(2, "subscriptions/listen",
                "{\"notifications\":{\"toolsListChanged\":true}}");
        HttpRequest.Builder builder = request()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(listen));
        modernHeaders("subscriptions/listen", null).forEach(builder::header);
        List<String> lines = new CopyOnWriteArrayList<>();
        HttpResponse<Stream<String>> response = HTTP.sendAsync(builder.timeout(Duration.ofSeconds(30)).build(),
                HttpResponse.BodyHandlers.ofLines()).get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/event-stream");
        Thread reader = new Thread(() -> {
            try (Stream<String> stream = response.body()) {
                stream.forEach(lines::add);
            } catch (RuntimeException e) {
                // the stream ends when the server stops
            }
        });
        reader.setDaemon(true);
        reader.start();

        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> lines.stream().anyMatch(line -> line.contains("notifications/subscriptions/acknowledged")));
        engine.toolAdded(tool("pong"));
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> lines.stream().anyMatch(line -> line.contains("notifications/tools/list_changed")));
    }

    /**
     * Starts the engine with the era, or with the constructor that does not name one when {@code null}.
     */
    private void start(McpProtocolEra era) throws Exception {
        start(era, false);
    }

    private void start(McpProtocolEra era, boolean stateless) throws Exception {
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
        engine.setModernKeepAliveInterval(null);
        engine.initialize(era != null
                ? new McpServerInfo(
                        "eras", "1.0", "/mcp", 0, 0, null, null, null, "Use the ping tool", null, null, null, null, null,
                        null, era, stateless)
                : new McpServerInfo(
                        "eras", "1.0", "/mcp", 0, 0, null, null, null, "Use the ping tool", null, null, null, null, null,
                        null));
        engine.start();
        engine.toolAdded(tool("ping"));
    }

    /**
     * A request of protocol version 2026-07-28: its parameters plus the {@code _meta} every such request carries.
     */
    private static String modernRequest(int id, String method, String params) {
        String meta = "\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"" + MODERN + "\","
                      + "\"io.modelcontextprotocol/clientCapabilities\":{},"
                      + "\"io.modelcontextprotocol/clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}";
        String withMeta = "{}".equals(params) ? "{" + meta + "}" : params.substring(0, params.length() - 1) + "," + meta + "}";
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + withMeta + "}";
    }

    private static Map<String, String> modernHeaders(String method, String name) {
        return name != null
                ? Map.of("MCP-Protocol-Version", MODERN, "Mcp-Method", method, "Mcp-Name", name)
                : Map.of("MCP-Protocol-Version", MODERN, "Mcp-Method", method);
    }

    private HttpResponse<String> post(String body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = request()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get() throws Exception {
        return HTTP.send(request().header("Accept", "text/event-stream").GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private int delete() throws Exception {
        return HTTP.send(request().header("Mcp-Session-Id", "any").DELETE().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private HttpRequest.Builder request() {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/mcp"))
                .timeout(Duration.ofSeconds(10));
    }

    private static McpServerTool tool(String name) {
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
                return arguments -> new McpToolCallResult("pong", false);
            }
        };
    }
}
