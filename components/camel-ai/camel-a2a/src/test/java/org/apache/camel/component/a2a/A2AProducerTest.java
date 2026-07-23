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
package org.apache.camel.component.a2a;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.streaming.SseEventIterator;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2AProducerTest extends CamelTestSupport {

    private static HttpServer mockServer;
    private static int port;
    private static volatile String lastTaskListQuery;
    private static volatile String lastAuthHeader;
    private static volatile String lastAuthAuthorization;
    private static volatile String lastQueryAuthQuery;
    private static volatile String lastRestContentType;
    private static volatile String lastRestAccept;
    private static volatile String lastJsonRpcContentType;
    private static volatile String lastJsonRpcAccept;

    @BeforeAll
    static void startMockServer() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = mockServer.getAddress().getPort();

        // Agent card endpoint
        mockServer.createContext("/.well-known/agent-card.json", exchange -> {
            String card = "{\"name\":\"Mock Agent\",\"version\":\"1.0.0\","
                          + "\"supportedInterfaces\":[{\"url\":\"http://localhost:" + port
                          + "\",\"protocolBinding\":\"HTTP+JSON\"},{\"url\":\"http://localhost:" + port
                          + "\",\"protocolBinding\":\"JSONRPC\"}]}";
            byte[] body = card.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/rest-only/.well-known/agent-card.json", exchange -> {
            String card = "{\"name\":\"REST Only Agent\",\"version\":\"1.0.0\","
                          + "\"supportedInterfaces\":[{\"url\":\"http://localhost:" + port
                          + "\",\"protocolBinding\":\"HTTP+JSON\"}]}";
            byte[] body = card.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/auth/.well-known/agent-card.json", exchange -> {
            String card = "{\"name\":\"Header Auth Agent\",\"version\":\"1.0.0\","
                          + "\"supportedInterfaces\":[{\"url\":\"http://localhost:" + port
                          + "/auth\",\"protocolBinding\":\"HTTP+JSON\"}],"
                          + "\"securitySchemes\":{\"agentKey\":{\"apiKeySecurityScheme\":"
                          + "{\"location\":\"header\",\"name\":\"X-Agent-Key\"}}},"
                          + "\"securityRequirements\":[{\"schemes\":{\"agentKey\":{\"list\":[]}}}]}";
            byte[] body = card.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/query-auth/.well-known/agent-card.json", exchange -> {
            String card = "{\"name\":\"Query Auth Agent\",\"version\":\"1.0.0\","
                          + "\"supportedInterfaces\":[{\"url\":\"http://localhost:" + port
                          + "/query-auth\",\"protocolBinding\":\"HTTP+JSON\"}],"
                          + "\"securitySchemes\":{\"queryKey\":{\"apiKeySecurityScheme\":"
                          + "{\"location\":\"query\",\"name\":\"agent_key\"}}},"
                          + "\"securityRequirements\":[{\"schemes\":{\"queryKey\":{\"list\":[]}}}]}";
            byte[] body = card.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // SendMessage endpoint (REST)
        mockServer.createContext("/message:send", exchange -> {
            lastRestContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            lastRestAccept = exchange.getRequestHeaders().getFirst("Accept");
            // Return a task response
            String responseJson = "{\"task\":{\"id\":\"task-001\",\"contextId\":\"ctx-001\","
                                  + "\"status\":{\"state\":\"TASK_STATE_COMPLETED\",\"timestamp\":\"2026-05-19T20:00:00.000Z\"},"
                                  + "\"history\":[{\"messageId\":\"msg-resp\",\"role\":\"agent\","
                                  + "\"parts\":[{\"text\":\"Mock response\"}]}]}}";
            byte[] body = responseJson.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/a2a+json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/auth/message:send", exchange -> {
            lastAuthHeader = exchange.getRequestHeaders().getFirst("X-Agent-Key");
            lastAuthAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            String responseJson = "{\"task\":{\"id\":\"task-auth\",\"contextId\":\"ctx-auth\","
                                  + "\"status\":{\"state\":\"TASK_STATE_COMPLETED\"}}}";
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/a2a+json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/query-auth/message:send", exchange -> {
            lastQueryAuthQuery = exchange.getRequestURI().getRawQuery();
            String responseJson = "{\"task\":{\"id\":\"task-query-auth\",\"contextId\":\"ctx-query-auth\","
                                  + "\"status\":{\"state\":\"TASK_STATE_COMPLETED\"}}}";
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/a2a+json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // JSON-RPC endpoint — handles both regular and streaming methods
        mockServer.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equals("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            lastJsonRpcContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            lastJsonRpcAccept = exchange.getRequestHeaders().getFirst("Accept");

            // Read request body to detect the JSON-RPC method
            byte[] reqBody = exchange.getRequestBody().readAllBytes();
            String reqStr = new String(reqBody, StandardCharsets.UTF_8);

            if (reqStr.contains("\"SendStreamingMessage\"")) {
                // Return SSE with JSON-RPC envelopes per event (A2A spec-compliant)
                String sseBody
                        = "data: {\"jsonrpc\":\"2.0\",\"result\":{\"statusUpdate\":{\"taskId\":\"task-jrpc-stream\","
                          + "\"status\":{\"state\":\"TASK_STATE_WORKING\"}}},\"id\":\"1\"}\n\n"
                          + "data: {\"jsonrpc\":\"2.0\",\"result\":{\"statusUpdate\":{\"taskId\":\"task-jrpc-stream\","
                          + "\"status\":{\"state\":\"TASK_STATE_COMPLETED\"},\"isFinal\":true}},\"id\":\"1\"}\n\n";
                byte[] body = sseBody.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } else {
                // Regular JSON-RPC response
                String responseJson
                        = "{\"jsonrpc\":\"2.0\",\"result\":{\"task\":{\"id\":\"task-jsonrpc\",\"contextId\":\"ctx-jrpc\","
                          + "\"status\":{\"state\":\"TASK_STATE_COMPLETED\",\"timestamp\":\"2026-05-20T00:00:00.000Z\"},"
                          + "\"history\":[{\"messageId\":\"msg-jrpc\",\"role\":\"agent\","
                          + "\"parts\":[{\"text\":\"JSON-RPC response\"}]}]}},\"id\":\"req-1\"}";
                byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });

        // SSE streaming endpoint
        mockServer.createContext("/message:stream", exchange -> {
            lastRestContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            lastRestAccept = exchange.getRequestHeaders().getFirst("Accept");
            String sseBody
                    = "data: {\"statusUpdate\":{\"taskId\":\"task-stream\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}\n\n"
                      + "data: {\"statusUpdate\":{\"taskId\":\"task-stream\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\"},\"isFinal\":true}}\n\n";
            byte[] body = sseBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // Error endpoints for negative-path tests
        mockServer.createContext("/error/404", exchange -> {
            byte[] body = "{\"code\":\"TaskNotFoundError\",\"message\":\"Not found\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/a2a+json");
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/error/500", exchange -> {
            byte[] body = "{\"code\":\"InternalError\",\"message\":\"Server exploded\"}".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/a2a+json");
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/error/stream-500", exchange -> {
            byte[] body = "Streaming error".getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/large-response/message:send", exchange -> {
            String responseJson = "{\"task\":{\"id\":\"task-large\",\"contextId\":\"ctx-large\","
                                  + "\"status\":{\"state\":\"TASK_STATE_COMPLETED\"},"
                                  + "\"history\":[{\"messageId\":\"msg-large\",\"role\":\"agent\","
                                  + "\"parts\":[{\"text\":\"This response intentionally exceeds the configured cap\"}]}]}}";
            byte[] body = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/a2a+json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.createContext("/redirect/message:send", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://localhost:" + port + "/message:send");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // SSE subscribe endpoint with heartbeat
        mockServer.createContext("/tasks/task-sub:subscribe", exchange -> {
            String sseBody = ":\n\n"
                             + "data: {\"statusUpdate\":{\"taskId\":\"task-sub\",\"status\":{\"state\":\"TASK_STATE_WORKING\"}}}\n\n"
                             + ":\n\n"
                             + "data: {\"statusUpdate\":{\"taskId\":\"task-sub\",\"status\":{\"state\":\"TASK_STATE_COMPLETED\"},\"isFinal\":true}}\n\n";
            byte[] body = sseBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // Task list endpoint — captures query string for assertions
        mockServer.createContext("/tasks", exchange -> {
            lastTaskListQuery = exchange.getRequestURI().getRawQuery();
            String responseJson = "{\"tasks\":[{\"id\":\"task-list-1\",\"contextId\":\"ctx-123\","
                                  + "\"status\":{\"state\":\"TASK_STATE_COMPLETED\","
                                  + "\"timestamp\":\"2026-06-12T00:00:00.000Z\"}}]}";
            byte[] body = responseJson.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/a2a+json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        mockServer.start();
    }

    @AfterAll
    static void stopMockServer() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("direct:send").toF("a2a:http://localhost:%d/.well-known/agent-card.json?dataFormat=POJO", port);
                fromF("direct:send-jsonrpc")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?protocolBinding=jsonrpc&dataFormat=POJO",
                                port);
                // host/port config overrides card's supportedInterfaces URL
                fromF("direct:send-host-override")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?host=http://localhost&port=%d&dataFormat=POJO",
                                port, port);
                // host-only (no port, no basePath) still works
                fromF("direct:send-host-only")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?host=http://localhost:%d&dataFormat=POJO",
                                port, port);
                // streaming producer
                fromF("direct:send-stream")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?operation=MESSAGE_STREAM", port);
                fromF("direct:subscribe")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?operation=TASK_SUBSCRIBE", port);
                fromF("direct:send-stream-jsonrpc")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?protocolBinding=jsonrpc&operation=MESSAGE_STREAM",
                                port);
                // dataFormat test routes
                fromF("direct:send-payload")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?dataFormat=PAYLOAD", port);
                fromF("direct:send-raw")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?dataFormat=RAW", port);
                fromF("direct:send-default")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json", port);
                // Task list route
                fromF("direct:list-tasks")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?operation=TASK_LIST&dataFormat=POJO",
                                port);
                fromF("direct:send-auth-header")
                        .toF("a2a:http://localhost:%d/auth?apiKey=agent-secret&dataFormat=POJO", port);
                fromF("direct:send-auth-query")
                        .toF("a2a:http://localhost:%d/query-auth?apiKey=query-secret&dataFormat=POJO", port);
                // Error routes — use host+basePath override to target error endpoints
                fromF("direct:send-error-404")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?host=http://localhost:%d&basePath=/error/404",
                                port, port);
                fromF("direct:send-error-500")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?host=http://localhost:%d&basePath=/error/500",
                                port, port);
                fromF("direct:send-stream-error-500")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?host=http://localhost:%d&basePath=/error/stream-500&operation=MESSAGE_STREAM",
                                port, port);
                fromF("direct:send-large-response")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?host=http://localhost:%d&basePath=/large-response&maxPayloadSize=32",
                                port, port);
                fromF("direct:send-redirect-default")
                        .toF("a2a:http://localhost:%d/.well-known/agent-card.json?host=http://localhost:%d&basePath=/redirect",
                                port, port);
            }
        };
    }

    @Test
    void sendMessageAndReceiveTask() {
        lastRestContentType = null;
        lastRestAccept = null;

        Exchange exchange = template.request("direct:send", e -> e.getIn().setBody("Hello agent"));
        // parseResponse now sets body = Task object, not extracted text
        assertThat(exchange.getIn().getBody()).isInstanceOf(Task.class);
        Task task = exchange.getIn().getBody(Task.class);
        assertThat(task).isNotNull();
        assertThat(task.id()).isEqualTo("task-001");
        assertThat(task.status().state()).isEqualTo(TaskState.COMPLETED);
        // Also available via property
        Task propTask = exchange.getProperty(A2AConstants.RESPONSE_TASK, Task.class);
        assertThat(propTask).isSameAs(task);
        assertThat(lastRestContentType).isEqualTo(A2AConstants.CONTENT_TYPE);
        assertThat(lastRestAccept).isEqualTo(A2AConstants.CONTENT_TYPE);
    }

    @Test
    void sendMessageViaJsonRpc() {
        lastJsonRpcContentType = null;
        lastJsonRpcAccept = null;

        Exchange exchange = template.request("direct:send-jsonrpc", e -> e.getIn().setBody("Hello via JSON-RPC"));
        // parseResponse now sets body = Task object
        assertThat(exchange.getIn().getBody()).isInstanceOf(Task.class);
        Task task = exchange.getIn().getBody(Task.class);
        assertThat(task).isNotNull();
        assertThat(task.id()).isEqualTo("task-jsonrpc");
        assertThat(task.status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(lastJsonRpcContentType).isEqualTo(A2AConstants.JSONRPC_CONTENT_TYPE);
        assertThat(lastJsonRpcAccept).isEqualTo(A2AConstants.JSONRPC_CONTENT_TYPE);
    }

    @Test
    void hostConfigOverridesCardUrl() {
        Exchange exchange = template.request("direct:send-host-override", e -> e.getIn().setBody("Hello via host override"));
        assertThat(exchange.getIn().getBody()).isInstanceOf(Task.class);
        Task task = exchange.getIn().getBody(Task.class);
        assertThat(task).isNotNull();
        assertThat(task.id()).isEqualTo("task-001");
    }

    @Test
    void hostOnlyConfigWorksWithoutSeparatePort() {
        Exchange exchange = template.request("direct:send-host-only", e -> e.getIn().setBody("Hello host-only"));
        assertThat(exchange.getIn().getBody()).isInstanceOf(Task.class);
        Task task = exchange.getIn().getBody(Task.class);
        assertThat(task).isNotNull();
        assertThat(task.id()).isEqualTo("task-001");
    }

    @Test
    void sendMessageWithPayloadDataFormat() {
        Exchange exchange = template.request("direct:send-payload", e -> e.getIn().setBody("Hello agent"));
        assertThat(exchange.getIn().getBody()).isInstanceOf(String.class);
        String body = exchange.getIn().getBody(String.class);
        assertThat(body).isEqualTo("Mock response");
    }

    @Test
    void sendMessageWithRawDataFormat() {
        Exchange exchange = template.request("direct:send-raw", e -> e.getIn().setBody("Hello agent"));
        assertThat(exchange.getIn().getBody()).isInstanceOf(String.class);
        String body = exchange.getIn().getBody(String.class);
        assertThat(body).contains("task-001");
        assertThat(body).contains("TASK_STATE_COMPLETED");
        assertThat(body).contains("Mock response");
    }

    @Test
    void sendMessageDefaultDataFormatIsPayload() {
        Exchange exchange = template.request("direct:send-default", e -> e.getIn().setBody("Hello agent"));
        // Default dataFormat is PAYLOAD — body should be extracted text, not a Task object
        assertThat(exchange.getIn().getBody()).isInstanceOf(String.class);
        String body = exchange.getIn().getBody(String.class);
        assertThat(body).isEqualTo("Mock response");
    }

    @Test
    void sendStreamingMessageReceivesSSEEvents() {
        lastRestContentType = null;
        lastRestAccept = null;

        Object result = template.requestBody("direct:send-stream", "Stream this");
        assertThat(result).isInstanceOf(SseEventIterator.class);
        List<StreamResponse> events = drainIterator((SseEventIterator) result);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getStatusUpdate()).isNotNull();
        assertThat(events.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        assertThat(events.get(1).getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(events.get(1).getStatusUpdate().isFinal()).isTrue();
        assertThat(lastRestContentType).isEqualTo(A2AConstants.CONTENT_TYPE);
        assertThat(lastRestAccept).isEqualTo(A2AConstants.SSE_CONTENT_TYPE);
    }

    @Test
    void sendStreamingMessageViaJsonRpc() {
        lastJsonRpcContentType = null;
        lastJsonRpcAccept = null;

        Object result = template.requestBody("direct:send-stream-jsonrpc", "Stream via JSON-RPC");
        assertThat(result).isInstanceOf(SseEventIterator.class);
        List<StreamResponse> events = drainIterator((SseEventIterator) result);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getStatusUpdate()).isNotNull();
        assertThat(events.get(0).getStatusUpdate().taskId()).isEqualTo("task-jrpc-stream");
        assertThat(events.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        assertThat(events.get(1).getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(events.get(1).getStatusUpdate().isFinal()).isTrue();
        assertThat(lastJsonRpcContentType).isEqualTo(A2AConstants.JSONRPC_CONTENT_TYPE);
        assertThat(lastJsonRpcAccept).isEqualTo(A2AConstants.SSE_CONTENT_TYPE);
    }

    @Test
    void subscribeToTaskFiltersHeartbeats() {
        Object result = template.requestBodyAndHeader(
                "direct:subscribe", null, "CamelA2ATaskId", "task-sub");
        assertThat(result).isInstanceOf(SseEventIterator.class);
        List<StreamResponse> events = drainIterator((SseEventIterator) result);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getStatusUpdate().taskId()).isEqualTo("task-sub");
        assertThat(events.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        assertThat(events.get(1).getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
    }

    // ---- Negative-path tests ----

    @Test
    void producerThrowsOn404Response() {
        Exchange exchange = template.request("direct:send-error-404", e -> e.getIn().setBody("Hello"));
        assertThat(exchange.getException()).isNotNull();
        assertThat(exchange.getException()).isInstanceOf(RuntimeException.class);
        assertThat(exchange.getException().getMessage()).contains("404");
    }

    @Test
    void producerThrowsOn500Response() {
        Exchange exchange = template.request("direct:send-error-500", e -> e.getIn().setBody("Hello"));
        assertThat(exchange.getException()).isNotNull();
        assertThat(exchange.getException()).isInstanceOf(RuntimeException.class);
        assertThat(exchange.getException().getMessage()).contains("500");
    }

    @Test
    void producerThrowsOnStreamingErrorResponse() {
        Exchange exchange = template.request("direct:send-stream-error-500", e -> e.getIn().setBody("Stream this"));
        assertThat(exchange.getException()).isNotNull();
        assertThat(exchange.getException()).isInstanceOf(RuntimeException.class);
        assertThat(exchange.getException().getMessage()).contains("500");
        assertThat(exchange.getException().getMessage()).containsIgnoringCase("streaming");
    }

    @Test
    void producerCapsNonStreamingResponseBody() {
        Exchange exchange = template.request("direct:send-large-response", e -> e.getIn().setBody("Hello"));
        assertThat(exchange.getException()).isNotNull();
        assertThat(exchange.getException().getMessage()).contains("A2A response exceeds maximum size: 32 bytes");
    }

    @Test
    void producerDoesNotFollowRedirectsByDefault() {
        Exchange exchange = template.request("direct:send-redirect-default", e -> e.getIn().setBody("Hello"));
        assertThat(exchange.getException()).isNotNull();
        assertThat(exchange.getException().getMessage()).contains("302");
    }

    @Test
    void producerRequiresCardInterfaceForConfiguredProtocolBinding() {
        Exchange exchange = template.request(
                "a2a:http://localhost:" + port + "/rest-only?protocolBinding=JSONRPC",
                e -> e.getIn().setBody("Hello"));

        assertThat(exchange.getException()).isNotNull();
        assertThat(exchange.getException().getMessage())
                .contains("no supportedInterfaces URL for protocol binding JSONRPC");
    }

    @Test
    void taskListSendsQueryParams() {
        lastTaskListQuery = null;

        Exchange exchange = template.request("direct:list-tasks", e -> {
            e.getMessage().setHeader(A2AConstants.LIST_CONTEXT_ID, "ctx-123");
            e.getMessage().setHeader(A2AConstants.LIST_PAGE_SIZE, 5);
            e.getMessage().setHeader(A2AConstants.LIST_STATUS, "COMPLETED,FAILED");
        });

        assertThat(exchange.getException()).isNull();
        assertThat(lastTaskListQuery).isNotNull();
        assertThat(lastTaskListQuery).contains("contextId=ctx-123");
        assertThat(lastTaskListQuery).contains("pageSize=5");
        assertThat(lastTaskListQuery).contains("status=COMPLETED");
    }

    @Test
    void taskListWithNoParamsSendsNoQueryString() {
        lastTaskListQuery = null;

        Exchange exchange = template.request("direct:list-tasks", e -> {
            // No list headers set
        });

        assertThat(exchange.getException()).isNull();
        assertThat(lastTaskListQuery).isNull();
    }

    @Test
    void producerSendsCardNamedApiKeyHeaderAndRestoresExchangeHeaders() {
        lastAuthHeader = null;
        lastAuthAuthorization = null;

        Exchange exchange = template.request("direct:send-auth-header", e -> {
            e.getMessage().setBody("Hello auth");
            e.getMessage().setHeader("Authorization", "caller-token");
        });

        assertThat(exchange.getException()).isNull();
        assertThat(lastAuthHeader).isEqualTo("agent-secret");
        assertThat(lastAuthAuthorization).isNull();
        assertThat(exchange.getMessage().getHeader("Authorization")).isEqualTo("caller-token");
        assertThat(exchange.getMessage().getHeader("X-Agent-Key")).isNull();
    }

    @Test
    void producerSendsCardNamedApiKeyQueryParameter() {
        lastQueryAuthQuery = null;

        Exchange exchange = template.request("direct:send-auth-query", e -> e.getMessage().setBody("Hello query auth"));

        assertThat(exchange.getException()).isNull();
        assertThat(lastQueryAuthQuery).isEqualTo("agent_key=query-secret");
    }

    private static List<StreamResponse> drainIterator(SseEventIterator iterator) {
        List<StreamResponse> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }
}
