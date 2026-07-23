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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.auth.A2AAuthHandler;
import org.apache.camel.component.a2a.auth.A2ASecuritySchemeHandler;
import org.apache.camel.component.a2a.auth.ApiKeySchemeHandler;
import org.apache.camel.component.a2a.auth.HttpBearerSchemeHandler;
import org.apache.camel.component.a2a.auth.OAuth2SchemeHandler;
import org.apache.camel.component.a2a.auth.OpenIdConnectSchemeHandler;
import org.apache.camel.component.a2a.card.AgentCardLoader;
import org.apache.camel.component.a2a.model.AgentCapabilities;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.AgentExtension;
import org.apache.camel.component.a2a.model.ListTasksResponse;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SecurityScheme;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.state.InMemoryTaskStore;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for adversarial review coverage gaps (#47): header spoofing, eviction machinery, maxPayloadSize, auth
 * route-bypass, card loader negatives, JSON-RPC error codes.
 */
class A2AAdversarialCoverageTest extends CamelTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("noopRestConsumerFactory", new NoopRestConsumerFactory());
        context.getRestConfiguration().setComponent("noopRestConsumerFactory");
        return context;
    }

    // ---- Header spoofing ----

    @Test
    void filterInboundHeadersStripsCamelHeaders() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Test Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:test", component, config);
        endpoint.setAgentCardSource("test");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            SendMessageResponse response = new SendMessageResponse();
            response.setMessage(Message.builder()
                    .messageId("msg-spoof-response")
                    .role(Message.Role.ROLE_AGENT)
                    .parts(List.of(new TextPart("ok")))
                    .build());
            exchange.getMessage().setBody(
                    OBJECT_MAPPER.writeValueAsString(response));
        });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("CamelA2ASpoofed", "evil-value");
        exchange.getMessage().setHeader("camelSpoofed", "evil-value");
        exchange.getMessage().setHeader("org.apache.camel.injected", "injected-value");
        exchange.getMessage().setHeader("X-Custom-Header", "safe-value");

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(Message.builder()
                .messageId("msg-spoof")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("test")))
                .build());
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        assertNull(exchange.getMessage().getHeader("CamelA2ASpoofed"),
                "CamelA2A* headers should be stripped after handling");
        assertNull(exchange.getMessage().getHeader("camelSpoofed"),
                "camel* headers should be stripped (case-insensitive)");
        assertNull(exchange.getMessage().getHeader("org.apache.camel.injected"),
                "org.apache.camel.* headers should be stripped");
    }

    // ---- Eviction machinery ----

    @Test
    void expiredTerminalTaskEvictedOnRead() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.setCompletedTaskTtlMs(1);

        Task task = Task.builder()
                .id("t-expire")
                .status(new TaskStatus(TaskState.COMPLETED, null, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)))
                .build();
        store.put("t-expire", task);

        assertNull(store.get("t-expire"), "Expired terminal task should be evicted on read");
        assertThat(store.contains("t-expire")).isFalse();
    }

    @Test
    void capacityCapEvictsOldestTerminal() {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.setMaxStoredTasks(3);
        store.setCompletedTaskTtlMs(Long.MAX_VALUE);

        for (int i = 1; i <= 3; i++) {
            store.put("t-" + i, Task.builder()
                    .id("t-" + i)
                    .status(new TaskStatus(TaskState.COMPLETED))
                    .build());
        }
        assertEquals(3, store.size());

        store.put("t-4", Task.builder()
                .id("t-4")
                .status(new TaskStatus(TaskState.SUBMITTED))
                .build());

        assertThat(store.size()).isLessThanOrEqualTo(3);
        assertNotNull(store.get("t-4"), "Newest task should survive");
    }

    @Test
    void periodicCleanupTriggersOnInterval() throws Exception {
        InMemoryTaskStore store = new InMemoryTaskStore();
        store.setCompletedTaskTtlMs(1);
        store.setCleanupInterval(2);

        store.put("t-a", Task.builder()
                .id("t-a")
                .status(new TaskStatus(TaskState.COMPLETED, null, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)))
                .build());
        store.put("t-b", Task.builder()
                .id("t-b")
                .status(new TaskStatus(TaskState.COMPLETED, null, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1)))
                .build());

        store.put("t-trigger", Task.builder()
                .id("t-trigger").status(new TaskStatus(TaskState.SUBMITTED)).build());

        assertNotNull(store.get("t-trigger"));
        assertThat(store.contains("t-a")).isFalse();
        assertThat(store.contains("t-b")).isFalse();
    }

    // ---- maxPayloadSize ----

    @Test
    void sendMessageRejectsOversizedPayload() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Size Agent");
        config.setVersion("1.0.0");
        config.setMaxPayloadSize(50);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:test", component, config);
        endpoint.setAgentCardSource("test");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            throw new AssertionError("Route should not be reached for oversized payloads");
        });

        String largeBody = "{\"message\":{\"messageId\":\"msg\",\"role\":\"ROLE_USER\","
                           + "\"parts\":[{\"text\":\"" + "x".repeat(100) + "\"}]}}";

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(largeBody);

        consumer.handleSendMessage(exchange);

        Integer status = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        assertEquals(413, status, "Should return 413 for oversized payload");
    }

    // ---- Auth route-bypass proof ----

    @Test
    void validateAuthDefaultsToFailClosed() {
        A2AConfiguration config = new A2AConfiguration();

        assertThat(config.isValidateAuth()).isTrue();
    }

    @Test
    void protocolBindingAcceptsLegacyAliasesButStoresSpecValues() {
        A2AConfiguration config = new A2AConfiguration();

        config.setProtocolBinding("jsonrpc");
        assertThat(config.getProtocolBinding()).isEqualTo(A2AConstants.PROTOCOL_JSONRPC);

        config.setProtocolBinding("rest");
        assertThat(config.getProtocolBinding()).isEqualTo(A2AConstants.PROTOCOL_REST);
    }

    @Test
    void validateAuthRejectsUnauthenticatedRequest() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);
        config.setApiKey("correct-secret");

        Map<String, A2ASecuritySchemeHandler> handlers = Map.of(
                "http", new HttpBearerSchemeHandler(),
                "apiKey", new ApiKeySchemeHandler(),
                "oauth2", new OAuth2SchemeHandler(),
                "openIdConnect", new OpenIdConnectSchemeHandler());
        A2AAuthHandler handler = new A2AAuthHandler(config, handlers);

        AgentCard card = new AgentCard.Builder()
                .setName("secure-agent")
                .setSecuritySchemes(Map.of("apikey", SecurityScheme.apiKey("header", "Authorization")))
                .build();

        Exchange exchange = new DefaultExchange(context);
        // No Authorization header set — should be rejected

        assertThatThrownBy(() -> handler.validateConsumerAuth(exchange, card))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Missing API key");
    }

    @Test
    void listTasksFiltersOwnerBeforeApplyingPageSize() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Owner Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:test", component, config);
        endpoint.setAgentCardSource("test");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            exchange.getMessage().setHeader(A2AConstants.TASK_STATE, TaskState.WORKING.getProtoName());
            exchange.getMessage().setBody("ok");
        });

        String ownerTaskId = createOwnedTask(consumer, "owner-a");
        Task ownerTask = endpoint.getTaskStore().get(ownerTaskId);
        endpoint.getTaskStore().put(ownerTaskId, Task.builder(ownerTask)
                .status(new TaskStatus(TaskState.WORKING, null, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5)))
                .build());

        String otherTaskId = createOwnedTask(consumer, "owner-b");
        Task otherTask = endpoint.getTaskStore().get(otherTaskId);
        endpoint.getTaskStore().put(otherTaskId, Task.builder(otherTask)
                .status(new TaskStatus(TaskState.WORKING, null, OffsetDateTime.now(ZoneOffset.UTC)))
                .build());

        Exchange listExchange = new DefaultExchange(context);
        listExchange.setProperty(A2AConstants.USER_PROFILE, Map.of("subject", "owner-a"));
        listExchange.getMessage().setHeader(Exchange.HTTP_QUERY, "pageSize=1");

        consumer.handleListTasks(listExchange);

        ListTasksResponse response = OBJECT_MAPPER.readValue(
                listExchange.getMessage().getBody(byte[].class), ListTasksResponse.class);
        assertThat(response.tasks()).extracting(Task::id).containsExactly(ownerTaskId);
    }

    // ---- Card loader negatives ----

    @Test
    void cardLoaderThrowsOnMissingFile() {
        AgentCardLoader loader = new AgentCardLoader();
        assertThatThrownBy(() -> loader.load("file:/nonexistent/path/card.json"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void cardLoaderThrowsOnMissingClasspath() {
        AgentCardLoader loader = new AgentCardLoader();
        assertThatThrownBy(() -> loader.load("classpath:nonexistent/card.json"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void cardLoaderReturnsSkeletonForPlainName() throws Exception {
        AgentCardLoader loader = new AgentCardLoader();
        AgentCard card = loader.load("my-agent-name");
        assertNotNull(card, "Plain name should return a skeleton card");
    }

    // ---- JSON-RPC error code mapping ----

    @Test
    void jsonRpcTaskNotFoundReturns32001() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Error Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:test", component, config);
        endpoint.setAgentCardSource("test");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });
        consumer.doStart();

        String jsonRpcRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"GetTask\","
                                + "\"params\":{\"id\":\"nonexistent-task-id\"},\"id\":1}";

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(jsonRpcRequest);

        consumer.handleJsonRpcDispatch(exchange);

        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        JsonNode response = OBJECT_MAPPER.readTree(responseBody);

        assertThat(response.has("error")).isTrue();
        assertThat(response.get("error").get("code").asInt()).isEqualTo(-32001);
    }

    @Test
    void jsonRpcUnknownMethodReturns32601() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Error Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:test", component, config);
        endpoint.setAgentCardSource("test");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });
        consumer.doStart();

        String jsonRpcRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"UnknownMethod\","
                                + "\"params\":{},\"id\":2}";

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(jsonRpcRequest);

        consumer.handleJsonRpcDispatch(exchange);

        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        JsonNode response = OBJECT_MAPPER.readTree(responseBody);

        assertThat(response.has("error")).isTrue();
        int code = response.get("error").get("code").asInt();
        assertThat(code).isEqualTo(-32601);
    }

    @Test
    void jsonRpcSubscribeToTaskReturnsSseStream() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Subscribe Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        config.setAgentCard(AgentCard.builder().setCapabilities(capabilities).build());

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:test", component, config);
        endpoint.setAgentCardSource("test");
        endpoint.setCamelContext(context);
        endpoint.doStart();
        endpoint.getTaskStore().put("task-subscribe", Task.builder()
                .id("task-subscribe")
                .contextId("ctx-subscribe")
                .status(new TaskStatus(TaskState.WORKING))
                .build());

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        String jsonRpcRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"SubscribeToTask\","
                                + "\"params\":{\"id\":\"task-subscribe\"},\"id\":\"sub-1\"}";

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(jsonRpcRequest);

        consumer.handleJsonRpcDispatch(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo("text/event-stream");
        InputStream stream = exchange.getMessage().getBody(InputStream.class);
        byte[] buffer = new byte[512];
        int read = stream.read(buffer);
        String firstFrame = new String(buffer, 0, read, StandardCharsets.UTF_8);
        JsonNode envelope = OBJECT_MAPPER.readTree(firstFrame.substring("data: ".length()).trim());
        assertThat(envelope.get("result").get("task").get("id").asText()).isEqualTo("task-subscribe");
    }

    @Test
    void jsonRpcSubscribeToTerminalTaskReturnsUnsupportedOperationError() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Terminal Subscribe Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        config.setAgentCard(AgentCard.builder().setCapabilities(capabilities).build());

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:test", component, config);
        endpoint.setAgentCardSource("test");
        endpoint.setCamelContext(context);
        endpoint.doStart();
        endpoint.getTaskStore().put("task-terminal-subscribe", Task.builder()
                .id("task-terminal-subscribe")
                .contextId("ctx-terminal-subscribe")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build());

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        String jsonRpcRequest = "{\"jsonrpc\":\"2.0\",\"method\":\"SubscribeToTask\","
                                + "\"params\":{\"id\":\"task-terminal-subscribe\"},\"id\":\"sub-terminal\"}";

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(jsonRpcRequest);

        consumer.handleJsonRpcDispatch(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE)).isEqualTo(A2AConstants.JSONRPC_CONTENT_TYPE);
        JsonNode envelope = OBJECT_MAPPER.readTree(exchange.getMessage().getBody(byte[].class));
        assertThat(envelope.get("id").asText()).isEqualTo("sub-terminal");
        assertThat(envelope.get("error").get("code").asInt()).isEqualTo(-32004);
        assertThat(envelope.get("error").get("message").asText()).contains("already terminal");
    }

    @Test
    void statusUpdateEventDoesNotSerializeIsFinal() throws Exception {
        StreamResponse response = StreamResponse.ofStatusUpdate(
                TaskStatusUpdateEvent.builder()
                        .taskId("task-final")
                        .status(new TaskStatus(TaskState.COMPLETED))
                        .build());

        JsonNode node = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(response));

        assertThat(node.get("statusUpdate").has("isFinal")).isFalse();
    }

    @Test
    void pushConfigTokenIsWriteOnly() throws Exception {
        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("https://example.com/webhook");
        config.setToken("secret-token");

        JsonNode node = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(config));

        assertThat(node.has("token")).isFalse();
        TaskPushNotificationConfig parsed = OBJECT_MAPPER.readValue(
                "{\"url\":\"https://example.com/webhook\",\"token\":\"secret-token\"}",
                TaskPushNotificationConfig.class);
        assertThat(parsed.getToken()).isEqualTo("secret-token");
    }

    @Test
    void agentCapabilitiesExtensionsUseSpecArrayShape() throws Exception {
        AgentExtension extension = new AgentExtension();
        extension.setUri("https://example.com/ext");
        extension.setRequired(true);
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setExtensions(List.of(extension));

        JsonNode node = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(capabilities));

        assertThat(node.get("extensions").isArray()).isTrue();
        assertThat(node.get("extensions").get(0).get("uri").asText()).isEqualTo("https://example.com/ext");
    }

    // ---- Terminal state guard ----

    @Test
    void terminalStateGuardRejectsOverwrite() {
        InMemoryTaskStore store = new InMemoryTaskStore();

        store.put("t-done", Task.builder()
                .id("t-done").status(new TaskStatus(TaskState.COMPLETED)).build());

        store.put("t-done", Task.builder()
                .id("t-done").status(new TaskStatus(TaskState.WORKING)).build());

        Task task = store.get("t-done");
        assertThat(task.status().state()).isEqualTo(TaskState.COMPLETED);
    }

    // ---- Bearer token fail-closed ----

    @Test
    void bearerValidationFailsClosedWithoutConfig() {
        A2AConfiguration config = new A2AConfiguration();
        config.setValidateAuth(true);

        Map<String, A2ASecuritySchemeHandler> handlers = Map.of(
                "http", new HttpBearerSchemeHandler(),
                "apiKey", new ApiKeySchemeHandler(),
                "oauth2", new OAuth2SchemeHandler(),
                "openIdConnect", new OpenIdConnectSchemeHandler());
        A2AAuthHandler handler = new A2AAuthHandler(config, handlers);

        AgentCard card = new AgentCard.Builder()
                .setName("test")
                .setSecuritySchemes(Map.of("bearer", SecurityScheme.httpBearer()))
                .build();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader("Authorization", "Bearer some-token");

        assertThatThrownBy(() -> handler.validateConsumerAuth(exchange, card))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("cannot validate");
    }

    private String createOwnedTask(A2AConsumer consumer, String subject) throws Exception {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(A2AConstants.USER_PROFILE, Map.of("subject", subject));
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(Message.builder()
                .messageId("msg-" + subject)
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("hello")))
                .build());
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        SendMessageResponse response = OBJECT_MAPPER.readValue(
                exchange.getMessage().getBody(byte[].class), SendMessageResponse.class);
        return response.getTask().id();
    }
}
