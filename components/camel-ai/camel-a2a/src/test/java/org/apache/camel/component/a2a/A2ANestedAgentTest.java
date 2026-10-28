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
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.a2a.model.AgentCapabilities;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.operation.A2AOperations;
import org.apache.camel.component.a2a.protocol.A2AProtocol;
import org.apache.camel.component.a2a.protocol.JsonRpcProtocol;
import org.apache.camel.component.a2a.protocol.RestProtocol;
import org.apache.camel.component.a2a.streaming.SseEventIterator;
import org.apache.camel.component.a2a.util.A2AJsonMapper;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class A2ANestedAgentTest {
    private static final ObjectMapper MAPPER = A2AJsonMapper.instance();
    private DefaultCamelContext context;
    private HttpServer peer;
    private A2AProtocol protocol;
    private A2AConsumer consumer;
    private A2AEndpoint outer;
    private A2AProducer producer;
    private final AtomicReference<String> peerOperation = new AtomicReference<>();
    private final AtomicReference<JsonNode> peerRequest = new AtomicReference<>();
    private final AtomicReference<Exchange> routeExchange = new AtomicReference<>();
    private boolean messageReply;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        NoopRestConsumerFactory.bindTo(context);
        context.start();
        peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/", http -> {
            byte[] input = http.getRequestBody().readAllBytes();
            JsonNode request = input.length == 0 ? MAPPER.createObjectNode() : MAPPER.readTree(input);
            boolean rpc = request.has("method");
            String operation = rpc ? request.path("method").asText() : http.getRequestURI().getPath();
            peerOperation.set(operation);
            peerRequest.set(rpc ? request.path("params") : request);
            Message answer = Message.builder().messageId("peer-message").contextId("peer-context").taskId("peer-task")
                    .role(Message.Role.ROLE_AGENT).parts(List.of(new TextPart("peer answer"))).build();
            Task task = Task.builder().id("peer-task").contextId("peer-context")
                    .status(new TaskStatus(TaskState.WORKING, answer)).history(List.of(answer)).build();
            SendMessageResponse response = new SendMessageResponse();
            if (messageReply) {
                response.setMessage(answer);
            } else {
                response.setTask(task);
            }
            Object result = operation.equals("GetTask") || operation.startsWith("/tasks/") ? task : response;
            byte[] output = protocol.wrapResponse(result, rpc ? request.get("id") : null);
            http.getResponseHeaders().set("Content-Type", protocol.contentType());
            http.sendResponseHeaders(200, output.length);
            try (var stream = http.getResponseBody()) {
                stream.write(output);
            }
        });
        peer.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (consumer != null) {
            consumer.stop();
            consumer.shutdown();
        }
        if (context != null) {
            context.stop();
        }
        if (peer != null) {
            peer.stop(0);
        }
    }

    static Stream<Arguments> nestedCalls() {
        return Stream.of("HTTP+JSON", "JSONRPC").flatMap(binding -> Stream.of("sync", "async", "stream")
                .flatMap(mode -> Stream.of(false, true).map(message -> Arguments.of(binding, mode, message))));
    }

    @ParameterizedTest
    @MethodSource("nestedCalls")
    void downstreamResultsDoNotChangeConsumerTask(String binding, String mode, boolean message) throws Exception {
        messageReply = message;
        start(binding, mode, A2ADataFormat.PAYLOAD, A2AOperations.MESSAGE_SEND, exchange -> {
            routeExchange.set(exchange);
            producer.process(exchange);
            assertThat(A2AProgress.hasTaskContext(exchange)).isTrue();
            A2AProgress.emit(exchange, "after peer");
        });
        Exchange request = send(mode);
        String taskId;
        if (mode.equals("stream")) {
            List<StreamResponse> events = new ArrayList<>();
            try (SseEventIterator iterator
                    = new SseEventIterator(request.getMessage().getBody(InputStream.class), protocol, 5000)) {
                iterator.forEachRemaining(events::add);
            }
            assertThat(events).anySatisfy(event -> assertThat(event.getMessage()).isNotNull());
            taskId = events.get(0).getTask().id();
            assertThat(events).anySatisfy(event -> {
                assertThat(event.getStatusUpdate()).isNotNull();
                assertThat(event.getStatusUpdate().taskId()).isEqualTo(taskId);
                assertThat(event.getStatusUpdate().status().message()).isNotNull();
                assertThat(event.getStatusUpdate().status().message().parts()).contains(new TextPart("after peer"));
            });
        } else {
            SendMessageResponse response = response(request);
            assertThat(response.isTaskResponse()).isTrue();
            taskId = response.getTask().id();
        }
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Task task = outer.getTaskStore().get(taskId);
            assertThat(task).isNotNull();
            assertThat(task.status().state()).isEqualTo(TaskState.COMPLETED);
            assertThat(task.contextId()).isEqualTo("outer-context");
        });
        assertThat(taskId).isNotEqualTo("peer-task");
        assertThat(outer.getTaskStore().get("peer-task")).isNull();
        assertThat(peerOperation.get()).isEqualTo(binding.equals("JSONRPC") ? "SendMessage" : "/message:send");
        assertThat(peerRequest.get().path("message").hasNonNull("contextId")).isFalse();
        assertThat(routeExchange.get().getProperty(A2AConstants.REQUEST_TASK_ID)).isEqualTo(taskId);
        assertThat(routeExchange.get().getMessage().getHeader(A2AConstants.RESPONSE_TYPE))
                .isEqualTo(message ? "message" : "task");
    }

    @ParameterizedTest
    @ValueSource(strings = { "HTTP+JSON", "JSONRPC" })
    void endpointOperationAndExplicitReplyControlsAreIndependent(String binding) throws Exception {
        start(binding, "sync", A2ADataFormat.PAYLOAD, A2AOperations.TASK_GET, exchange -> {
            routeExchange.set(exchange);
            exchange.getMessage().setHeader(A2AConstants.TASK_ID, "peer-task");
            exchange.getMessage().setHeader(A2AConstants.REPLY_TASK_STATE, TaskState.INPUT_REQUIRED.getProtoName());
            producer.process(exchange);
        });
        SendMessageResponse response = response(send("sync"));
        assertThat(peerOperation.get()).isEqualTo(binding.equals("JSONRPC") ? "GetTask" : "/tasks/peer-task");
        assertThat(response.getTask().id()).isNotEqualTo("peer-task");
        assertThat(response.getTask().status().state()).isEqualTo(TaskState.INPUT_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = { "HTTP+JSON", "JSONRPC" })
    void explicitOperationEqualToIncomingMethodStillOverridesEndpoint(String binding) throws Exception {
        start(binding, "sync", A2ADataFormat.PAYLOAD, A2AOperations.TASK_GET, exchange -> {
            exchange.getMessage().setHeader(A2AConstants.OPERATION, "SendMessage");
            exchange.getMessage().setHeader(A2AConstants.REPLY_TYPE, "message");
            producer.process(exchange);
        });
        SendMessageResponse response = response(send("sync"));
        assertThat(peerOperation.get()).isEqualTo(binding.equals("JSONRPC") ? "SendMessage" : "/message:send");
        assertThat(response.isMessageResponse()).isTrue();
        assertThat(response.getMessage().contextId()).isEqualTo("outer-context");
        assertThat(response.getMessage().taskId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void pojoRequestUsesOnlyExplicitDownstreamIdentity(boolean explicit) throws Exception {
        start("HTTP+JSON", "sync", A2ADataFormat.POJO, A2AOperations.MESSAGE_SEND, exchange -> {
            if (explicit) {
                exchange.getMessage().setBody(Message.builder(exchange.getMessage().getBody(Message.class))
                        .taskId("selected-peer-task").contextId("selected-peer-context").build());
            }
            producer.process(exchange);
        });
        outer.getTaskStore().put("existing-outer", Task.builder().id("existing-outer").contextId("outer-context")
                .status(new TaskStatus(TaskState.WORKING)).build());
        response(send("sync", "existing-outer"));
        JsonNode message = peerRequest.get().path("message");
        if (explicit) {
            assertThat(message.path("taskId").asText()).isEqualTo("selected-peer-task");
            assertThat(message.path("contextId").asText()).isEqualTo("selected-peer-context");
        } else {
            assertThat(message.hasNonNull("taskId")).isFalse();
            assertThat(message.hasNonNull("contextId")).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "sync", "async", "stream" })
    void removingAndReplacingHeadersCannotRedirectConsumerContext(String mode) throws Exception {
        start("HTTP+JSON", mode, A2ADataFormat.PAYLOAD, A2AOperations.MESSAGE_SEND, exchange -> {
            routeExchange.set(exchange);
            producer.process(exchange);
            exchange.getMessage().removeHeaders("CamelA2A*");
            exchange.getMessage().setHeader(A2AConstants.REQUEST_TASK_ID, "wrong-task");
            exchange.getMessage().setHeader(A2AConstants.REQUEST_CONTEXT_ID, "wrong-context");
            exchange.getMessage().setHeader(A2AConstants.TASK_ID, "wrong-producer-task");
            assertThat(A2AProgress.hasTaskContext(exchange)).isTrue();
            A2AProgress.emit(exchange, "after header removal");
        });
        Exchange request = send(mode);
        String taskId;
        if (mode.equals("stream")) {
            List<StreamResponse> events = new ArrayList<>();
            try (SseEventIterator iterator
                    = new SseEventIterator(request.getMessage().getBody(InputStream.class), protocol, 5000)) {
                iterator.forEachRemaining(events::add);
            }
            taskId = events.get(0).getTask().id();
            assertThat(events).anySatisfy(event -> assertThat(event.getMessage()).isNotNull());
        } else {
            taskId = response(request).getTask().id();
        }
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(outer.getTaskStore().get(taskId).status().state()).isEqualTo(TaskState.COMPLETED);
            assertThat(outer.getTaskStore().get(taskId).contextId()).isEqualTo("outer-context");
        });
        assertThat(taskId).isEqualTo(routeExchange.get().getProperty(A2AConstants.REQUEST_TASK_ID));
        assertThat(outer.getTaskStore().get("wrong-task")).isNull();
        assertThat(outer.getTaskStore().get("peer-task")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void pojoReplyUsesConsumerIdentity(boolean messageOnly) throws Exception {
        messageReply = true;
        start("HTTP+JSON", "sync", A2ADataFormat.PAYLOAD, A2AOperations.MESSAGE_SEND, exchange -> {
            routeExchange.set(exchange);
            exchange.getMessage().setHeader(A2AConstants.REPLY_TYPE, messageOnly ? "message" : "task");
            producer.process(exchange);
        });
        producer.getEndpoint().getConfiguration().setDataFormat(A2ADataFormat.POJO);
        SendMessageResponse response = response(send("sync"));
        Message reply = messageOnly ? response.getMessage() : response.getTask().status().message();
        assertThat(reply.contextId()).isEqualTo("outer-context");
        assertThat(reply.taskId()).isEqualTo(messageOnly ? null : response.getTask().id());
        assertThat(reply.parts()).containsExactly(new TextPart("peer answer"));
        Message peerMessage = routeExchange.get().getMessage().getBody(Message.class);
        assertThat(peerMessage.taskId()).isEqualTo("peer-task");
        assertThat(peerMessage.contextId()).isEqualTo("peer-context");
    }

    private void start(String binding, String mode, A2ADataFormat dataFormat, A2AOperations operation, Processor route)
            throws Exception {
        protocol = binding.equals("JSONRPC") ? new JsonRpcProtocol() : new RestProtocol();
        A2AConfiguration remoteConfig = new A2AConfiguration();
        remoteConfig.setHost("http://127.0.0.1");
        remoteConfig.setPort(peer.getAddress().getPort());
        remoteConfig.setOperation(operation);
        remoteConfig.setProtocolBinding(binding);
        producer = new A2AProducer(endpoint("peer", remoteConfig));
        A2AConfiguration config = new A2AConfiguration();
        config.setProtocolBinding(binding);
        config.setDataFormat(dataFormat);
        config.setReturnImmediately(mode.equals("async"));
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        config.setAgentCard(AgentCard.builder().setName("outer").setVersion("1.0").setCapabilities(capabilities).build());
        outer = endpoint("outer", config);
        consumer = new A2AConsumer(outer, route);
        consumer.start();
    }

    private A2AEndpoint endpoint(String name, A2AConfiguration config) throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint("a2a:" + name, component, config);
        endpoint.setAgentCardSource(name);
        context.addEndpoint("a2a:" + name, endpoint);
        endpoint.start();
        return endpoint;
    }

    private Exchange send(String mode) throws Exception {
        return send(mode, null);
    }

    private Exchange send(String mode, String taskId) throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(Message.builder().messageId("caller-message").contextId("outer-context").taskId(taskId)
                .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("hello"))).build());
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage()
                .setBody(protocol.wrapRequest(mode.equals("stream") ? "SendStreamingMessage" : "SendMessage", request));
        if (protocol instanceof JsonRpcProtocol) {
            consumer.handleJsonRpcDispatch(exchange);
        } else if (mode.equals("stream")) {
            consumer.handleMessageStream(exchange);
        } else {
            consumer.handleSendMessage(exchange);
        }
        return exchange;
    }

    private SendMessageResponse response(Exchange exchange) {
        return (SendMessageResponse) protocol.unwrapResponse(exchange.getMessage().getBody(byte[].class),
                SendMessageResponse.class);
    }
}
