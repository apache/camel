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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.a2a.extension.A2AExtensionHandler;
import org.apache.camel.component.a2a.model.A2AError;
import org.apache.camel.component.a2a.model.AgentCapabilities;
import org.apache.camel.component.a2a.model.AgentCard;
import org.apache.camel.component.a2a.model.AgentExtension;
import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.ListTasksResponse;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageConfiguration;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.SendMessageResponse;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.state.A2ATaskStore;
import org.apache.camel.component.a2a.state.InMemoryTaskStore;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2AConsumerTest {

    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    private CamelContext context;

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private static void enableStreaming(A2AConfiguration config) {
        enableCapabilities(config, true, false);
    }

    private static void enablePushNotifications(A2AConfiguration config) {
        enableCapabilities(config, false, true);
    }

    private static void enableCapabilities(
            A2AConfiguration config, boolean streaming, boolean pushNotifications) {
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(streaming);
        capabilities.setPushNotifications(pushNotifications);
        config.setAgentCard(AgentCard.builder().setCapabilities(capabilities).build());
    }

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        NoopRestConsumerFactory.bindTo(context);
        context.getRestConfiguration().setComponent("noopRestConsumerFactory");
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void handleAgentCardReturnsCard() throws Exception {
        // Create configuration
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Test Agent");
        config.setVersion("1.0.0");
        config.setDescription("A test agent");

        // Create component and endpoint
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:test-agent", component, config);
        endpoint.setAgentCardSource("test-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart(); // Loads card

        // Create consumer with a dummy processor
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        // Create exchange
        Exchange exchange = new DefaultExchange(context);

        // Handle agent card request
        consumer.handleAgentCardRequest(exchange);

        // Verify response
        byte[] body = exchange.getMessage().getBody(byte[].class);
        assertNotNull(body);

        AgentCard card = OBJECT_MAPPER.readValue(body, AgentCard.class);
        assertEquals("Test Agent", card.getName());
        assertEquals("1.0.0", card.getVersion());
        assertEquals("A test agent", card.getDescription());

        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        assertEquals(A2AConstants.CONTENT_TYPE, contentType);
    }

    @Test
    void handleSendMessageProcessesRequest() throws Exception {
        // Create configuration
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Echo Agent");
        config.setVersion("1.0.0");
        config.setDescription("Echoes messages");

        // Create component and endpoint
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:echo-agent", component, config);
        endpoint.setAgentCardSource("echo-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        // Create consumer with an echo processor
        Processor echoProcessor = exchange -> {
            String input = exchange.getMessage().getBody(String.class);
            exchange.getMessage().setBody("Echo: " + input);
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, echoProcessor);

        // Create SendMessage request
        Message message = Message.builder()
                .messageId("msg-123")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Hello, agent!")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        String requestJson = OBJECT_MAPPER.writeValueAsString(request);

        // Create exchange
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(requestJson);

        // Handle send message
        consumer.handleSendMessage(exchange);

        // Verify response
        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        assertNotNull(responseBody);

        SendMessageResponse response = OBJECT_MAPPER.readValue(responseBody, SendMessageResponse.class);
        assertTrue(response.isTaskResponse());

        assertNotNull(response.getTask());
        assertNotNull(response.getTask().id());
        assertNotNull(response.getTask().contextId());

        // Verify task was persisted
        String taskId = response.getTask().id();
        assertNotNull(endpoint.getTaskStore().get(taskId));

        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        assertEquals(A2AConstants.CONTENT_TYPE, contentType);
    }

    @Test
    void handleSendMessageWithContextId() throws Exception {
        // Create configuration
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Test Agent");
        config.setVersion("1.0.0");

        // Create component and endpoint
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:test-agent", component, config);
        endpoint.setAgentCardSource("test-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        // Create consumer
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            exchange.getMessage().setBody("Response");
        });

        // Create SendMessage request with context ID
        Message message = Message.builder()
                .messageId("msg-456")
                .role(Message.Role.ROLE_USER)
                .contextId("ctx-789")
                .parts(List.of(new TextPart("Follow-up message")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        String requestJson = OBJECT_MAPPER.writeValueAsString(request);

        // Create exchange
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(requestJson);

        // Handle send message
        consumer.handleSendMessage(exchange);

        // Verify response
        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        SendMessageResponse response = OBJECT_MAPPER.readValue(responseBody, SendMessageResponse.class);

        // Verify context ID is preserved
        assertEquals("ctx-789", response.getTask().contextId());
    }

    @Test
    void handleSendMessageWithPayloadDataFormat() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Payload Agent");
        config.setVersion("1.0.0");
        config.setDescription("Tests PAYLOAD dataFormat");
        config.setDataFormat(A2ADataFormat.PAYLOAD);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:payload-agent", component, config);
        endpoint.setAgentCardSource("payload-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        AtomicReference<Object> capturedBody = new AtomicReference<>();

        Processor captureProcessor = exchange -> {
            capturedBody.set(exchange.getMessage().getBody());
            exchange.getMessage().setBody("Processed");
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, captureProcessor);

        Message message = Message.builder()
                .messageId("msg-payload")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Hello from payload test")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        assertNotNull(capturedBody.get());
        assertInstanceOf(String.class, capturedBody.get());
        assertEquals("Hello from payload test", capturedBody.get());
    }

    @Test
    void handleSendMessageWithPojoDataFormat() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("POJO Agent");
        config.setVersion("1.0.0");
        config.setDescription("Tests POJO dataFormat");
        config.setDataFormat(A2ADataFormat.POJO);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:pojo-agent", component, config);
        endpoint.setAgentCardSource("pojo-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        AtomicReference<Object> capturedBody = new AtomicReference<>();

        Processor captureProcessor = exchange -> {
            capturedBody.set(exchange.getMessage().getBody());
            exchange.getMessage().setBody("Processed");
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, captureProcessor);

        Message message = Message.builder()
                .messageId("msg-pojo")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Hello from POJO test")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        assertNotNull(capturedBody.get());
        assertInstanceOf(Message.class, capturedBody.get());
        Message capturedMessage = (Message) capturedBody.get();
        assertEquals("msg-pojo", capturedMessage.messageId());
        assertEquals(Message.Role.ROLE_USER, capturedMessage.role());
        assertEquals(1, capturedMessage.parts().size());
        assertInstanceOf(TextPart.class, capturedMessage.parts().get(0));
        assertEquals("Hello from POJO test", ((TextPart) capturedMessage.parts().get(0)).text());
    }

    @Test
    void handleSendMessageWithRawDataFormat() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("RAW Agent");
        config.setVersion("1.0.0");
        config.setDescription("Tests RAW dataFormat");
        config.setDataFormat(A2ADataFormat.RAW);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:raw-agent", component, config);
        endpoint.setAgentCardSource("raw-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        AtomicReference<Object> capturedBody = new AtomicReference<>();

        Processor captureProcessor = exchange -> {
            capturedBody.set(exchange.getMessage().getBody());
            exchange.getMessage().setBody("Processed");
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, captureProcessor);

        Message message = Message.builder()
                .messageId("msg-raw")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Hello from RAW test")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        assertNotNull(capturedBody.get());
        assertInstanceOf(String.class, capturedBody.get());
        String jsonBody = (String) capturedBody.get();
        assertTrue(jsonBody.contains("msg-raw"), "RAW body should contain messageId");
        assertTrue(jsonBody.contains("Hello from RAW test"), "RAW body should contain message text");
        assertTrue(jsonBody.contains("ROLE_USER"), "RAW body should contain role");
    }

    @Test
    void handleGetTaskReturnsTask() throws Exception {
        // Create configuration
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Test Agent");
        config.setVersion("1.0.0");

        // Create component and endpoint
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:test-agent", component, config);
        endpoint.setAgentCardSource("test-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        // Create consumer
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        // Create and store a task
        Task task = Task.builder()
                .id("task-123")
                .contextId("ctx-456")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        endpoint.getTaskStore().put("task-123", task);

        // Create exchange with task ID in path
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-123");

        // Handle get task request
        consumer.handleGetTask(exchange);

        // Verify response
        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        assertNotNull(responseBody);

        Task responseTask = OBJECT_MAPPER.readValue(responseBody, Task.class);
        assertEquals("task-123", responseTask.id());
        assertEquals("ctx-456", responseTask.contextId());
        assertEquals(TaskState.WORKING, responseTask.status().state());

        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        assertEquals(A2AConstants.CONTENT_TYPE, contentType);
    }

    @Test
    void handleGetTaskReturns404ForMissing() throws Exception {
        // Create configuration
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Test Agent");
        config.setVersion("1.0.0");

        // Create component and endpoint
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:test-agent", component, config);
        endpoint.setAgentCardSource("test-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        // Create consumer
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        // Create exchange with non-existent task ID
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/nonexistent");

        // Handle get task request
        consumer.handleGetTask(exchange);

        // Verify error response
        Integer httpCode = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        assertEquals(404, httpCode);

        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        assertNotNull(responseBody);

        A2AError error = OBJECT_MAPPER.readValue(responseBody, A2AError.class);
        assertEquals("TaskNotFoundError", error.getCode());
        assertTrue(error.getMessage().contains("nonexistent"));
    }

    @Test
    void handleCancelTaskSetsStateToCanceled() throws Exception {
        // Create configuration
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Test Agent");
        config.setVersion("1.0.0");

        // Create component and endpoint
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:test-agent", component, config);
        endpoint.setAgentCardSource("test-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        // Create consumer
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        // Create and store a working task
        Task task = Task.builder()
                .id("task-789")
                .contextId("ctx-abc")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        endpoint.getTaskStore().put("task-789", task);

        // Create exchange with cancel path
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-789:cancel");

        // Handle cancel task request
        consumer.handleCancelTask(exchange);

        // Verify response
        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        assertNotNull(responseBody);

        Task responseTask = OBJECT_MAPPER.readValue(responseBody, Task.class);
        assertEquals("task-789", responseTask.id());
        assertEquals(TaskState.CANCELED, responseTask.status().state());

        // Verify task was updated in store
        Task storedTask = endpoint.getTaskStore().get("task-789");
        assertEquals(TaskState.CANCELED, storedTask.status().state());
    }

    @Test
    void handleCancelTaskRejectsTerminalTask() throws Exception {
        // Create configuration
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Test Agent");
        config.setVersion("1.0.0");

        // Create component and endpoint
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:test-agent", component, config);
        endpoint.setAgentCardSource("test-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        // Create consumer
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        // Create and store a completed task
        Task task = Task.builder()
                .id("task-completed")
                .contextId("ctx-xyz")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        endpoint.getTaskStore().put("task-completed", task);

        // Create exchange with cancel path
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-completed:cancel");

        // Handle cancel task request
        consumer.handleCancelTask(exchange);

        // Verify error response
        Integer httpCode = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        assertEquals(409, httpCode);

        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        assertNotNull(responseBody);

        A2AError error = OBJECT_MAPPER.readValue(responseBody, A2AError.class);
        assertEquals("TaskNotCancelableError", error.getCode());
        assertTrue(error.getMessage().contains("terminal state"));

        // Verify task state was not changed
        Task storedTask = endpoint.getTaskStore().get("task-completed");
        assertEquals(TaskState.COMPLETED, storedTask.status().state());
    }

    @Test
    void handleListTasksReturnsAllTasks() throws Exception {
        // Create configuration
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Test Agent");
        config.setVersion("1.0.0");

        // Create component and endpoint
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:test-agent", component, config);
        endpoint.setAgentCardSource("test-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        // Create consumer
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        // Store multiple tasks
        Task task1 = Task.builder()
                .id("task-1")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        endpoint.getTaskStore().put("task-1", task1);

        Task task2 = Task.builder()
                .id("task-2")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        endpoint.getTaskStore().put("task-2", task2);

        Task task3 = Task.builder()
                .id("task-3")
                .status(new TaskStatus(TaskState.SUBMITTED))
                .build();
        endpoint.getTaskStore().put("task-3", task3);

        // Create exchange
        Exchange exchange = new DefaultExchange(context);

        // Handle list tasks request
        consumer.handleListTasks(exchange);

        // Verify response
        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        assertNotNull(responseBody);

        ListTasksResponse listResponse = OBJECT_MAPPER.readValue(responseBody, ListTasksResponse.class);
        assertEquals(3, listResponse.tasks().size());

        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        assertEquals(A2AConstants.CONTENT_TYPE, contentType);
    }

    @Test
    void handleListTasksAppliesFiltersPaginationAndResponseShape() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("List Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:list-agent", component, config);
        endpoint.setAgentCardSource("list-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        String cutoffText = cutoff.toString();
        endpoint.getTaskStore().put("task-new", listTask("task-new", "ctx-list",
                TaskState.COMPLETED, cutoff.plusMinutes(2)));
        endpoint.getTaskStore().put("task-next", listTask("task-next", "ctx-list",
                TaskState.COMPLETED, cutoff.plusMinutes(1)));
        endpoint.getTaskStore().put("task-old", listTask("task-old", "ctx-list",
                TaskState.COMPLETED, cutoff.minusMinutes(1)));
        endpoint.getTaskStore().put("task-working", listTask("task-working", "ctx-list",
                TaskState.WORKING, cutoff.plusMinutes(3)));
        endpoint.getTaskStore().put("task-other-context", listTask("task-other-context", "ctx-other",
                TaskState.COMPLETED, cutoff.plusMinutes(4)));

        Exchange firstPage = new DefaultExchange(context);
        firstPage.getMessage().setHeader(Exchange.HTTP_QUERY,
                "contextId=ctx-list&pageSize=1&status=TASK_STATE_COMPLETED"
                                                              + "&statusTimestampAfter=" + cutoffText
                                                              + "&includeArtifacts=false&historyLength=1");

        consumer.handleListTasks(firstPage);

        ListTasksResponse firstResponse = OBJECT_MAPPER.readValue(
                firstPage.getMessage().getBody(byte[].class), ListTasksResponse.class);
        assertThat(firstResponse.tasks()).extracting(Task::id).containsExactly("task-new");
        assertThat(firstResponse.nextPageToken()).isNotBlank().isNotEqualTo("1");
        assertThat(firstResponse.pageSize()).isEqualTo(1);
        assertThat(firstResponse.totalSize()).isEqualTo(2);
        Task shapedTask = firstResponse.tasks().get(0);
        assertThat(shapedTask.artifacts()).isNull();
        assertThat(shapedTask.history()).extracting(Message::messageId).containsExactly("task-new-history-2");

        endpoint.getTaskStore().put("task-inserted", listTask("task-inserted", "ctx-list",
                TaskState.COMPLETED, cutoff.plusMinutes(5)));

        Exchange secondPage = new DefaultExchange(context);
        secondPage.getMessage().setHeader(Exchange.HTTP_QUERY,
                "contextId=ctx-list&pageSize=1&pageToken=" + firstResponse.nextPageToken() + "&status=COMPLETED"
                                                               + "&statusTimestampAfter=" + cutoffText);

        consumer.handleListTasks(secondPage);

        ListTasksResponse secondResponse = OBJECT_MAPPER.readValue(
                secondPage.getMessage().getBody(byte[].class), ListTasksResponse.class);
        assertThat(secondResponse.tasks()).extracting(Task::id).containsExactly("task-next");
        assertThat(secondResponse.nextPageToken()).isNull();
        assertThat(secondResponse.totalSize()).isEqualTo(2);
    }

    @Test
    void handleListTasksRejectsInvalidPaginationParams() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("List Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:list-agent-invalid", component, config);
        endpoint.setAgentCardSource("list-agent-invalid");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_QUERY, "pageSize=0");

        consumer.handleListTasks(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("InvalidParamsError");
    }

    @Test
    void handleListTasksRejectsInvalidPageToken() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("List Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:list-agent-invalid-token", component, config);
        endpoint.setAgentCardSource("list-agent-invalid-token");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_QUERY, "pageToken=1");

        consumer.handleListTasks(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("InvalidParamsError");
        assertThat(error.getMessage()).contains("pageToken is invalid");
    }

    @Test
    void jsonRpcListTasksAppliesRequestFields() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("JSON-RPC List Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:jsonrpc-list-agent", component, config);
        endpoint.setAgentCardSource("jsonrpc-list-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        endpoint.getTaskStore().put("task-json-new", listTask("task-json-new", "ctx-json",
                TaskState.COMPLETED, cutoff.plusMinutes(2)));
        endpoint.getTaskStore().put("task-json-next", listTask("task-json-next", "ctx-json",
                TaskState.COMPLETED, cutoff.plusMinutes(1)));
        endpoint.getTaskStore().put("task-json-working", listTask("task-json-working", "ctx-json",
                TaskState.WORKING, cutoff.plusMinutes(3)));

        Map<String, Object> jsonRpcRequest = Map.of(
                "jsonrpc", "2.0",
                "method", "ListTasks",
                "params", Map.of(
                        "contextId", "ctx-json",
                        "pageSize", 1,
                        "status", List.of("TASK_STATE_COMPLETED"),
                        "statusTimestampAfter", cutoff.toString(),
                        "includeArtifacts", false,
                        "historyLength", 1),
                "id", "list-1");
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(jsonRpcRequest));

        consumer.handleJsonRpcDispatch(exchange);

        JsonNode envelope = OBJECT_MAPPER.readTree(exchange.getMessage().getBody(byte[].class));
        ListTasksResponse response = OBJECT_MAPPER.treeToValue(envelope.get("result"), ListTasksResponse.class);
        assertThat(response.tasks()).extracting(Task::id).containsExactly("task-json-new");
        assertThat(response.nextPageToken()).isNotBlank().isNotEqualTo("1");
        assertThat(response.totalSize()).isEqualTo(2);
        assertThat(response.tasks().get(0).artifacts()).isNull();
        assertThat(response.tasks().get(0).history()).extracting(Message::messageId)
                .containsExactly("task-json-new-history-2");
    }

    // ---- returnImmediately tests ----

    @Test
    void returnImmediatelyReturnsSUBMITTED() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Async Agent");
        config.setVersion("1.0.0");
        config.setReturnImmediately(true);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:async-agent", component, config);
        endpoint.setAgentCardSource("async-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        Processor slowProcessor = exchange -> {
            exchange.getMessage().setBody("Done");
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, slowProcessor);
        consumer.doStart();

        Message message = Message.builder()
                .messageId("msg-async")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Process this async")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        SendMessageResponse response = OBJECT_MAPPER.readValue(responseBody, SendMessageResponse.class);

        assertTrue(response.isTaskResponse());
        assertEquals(TaskState.SUBMITTED, response.getTask().status().state());

        String taskId = response.getTask().id();
        assertNotNull(taskId);

        // Wait for async processing to complete
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Task storedTask = endpoint.getTaskStore().get(taskId);
                    assertNotNull(storedTask);
                    assertNotNull(storedTask.status());
                    assertTrue(storedTask.status().state().isTerminal());
                });

        consumer.doStop();
        consumer.doShutdown();
    }

    @Test
    void returnImmediatelyPerRequestConfig() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Async Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:async-agent", component, config);
        endpoint.setAgentCardSource("async-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            exchange.getMessage().setBody("Done");
        });
        consumer.doStart();

        Message message = Message.builder()
                .messageId("msg-per-req")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Async per request")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);
        request.setConfiguration(Map.of("returnImmediately", true));

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        SendMessageResponse response = OBJECT_MAPPER.readValue(responseBody, SendMessageResponse.class);

        assertEquals(TaskState.SUBMITTED, response.getTask().status().state());

        consumer.doStop();
        consumer.doShutdown();
    }

    @Test
    void blockingFalsePerRequestConfigReturnsSubmitted() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Blocking Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:blocking-agent", component, config);
        endpoint.setAgentCardSource("blocking-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            exchange.getMessage().setBody("Done");
        });
        consumer.doStart();

        Message message = Message.builder()
                .messageId("msg-blocking-false")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Async via blocking=false")))
                .build();
        SendMessageConfiguration configuration = new SendMessageConfiguration();
        configuration.setBlocking(false);
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);
        request.setConfiguration(configuration);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        SendMessageResponse response = OBJECT_MAPPER.readValue(
                exchange.getMessage().getBody(byte[].class), SendMessageResponse.class);
        assertThat(response.getTask().status().state()).isEqualTo(TaskState.SUBMITTED);
        String taskId = response.getTask().id();
        await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(endpoint.getTaskStore().get(taskId).status().state().isTerminal()).isTrue());

        consumer.doStop();
        consumer.doShutdown();
    }

    @Test
    void sendMessageHistoryLengthZeroShapesTaskResponse() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("History Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:history-agent", component, config);
        endpoint.setAgentCardSource("history-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> exchange.getMessage().setBody("Done"));

        Message message = Message.builder()
                .messageId("msg-history-zero")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Shape history")))
                .build();
        SendMessageConfiguration configuration = new SendMessageConfiguration();
        configuration.setHistoryLength(0);
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);
        request.setConfiguration(configuration);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        SendMessageResponse response = OBJECT_MAPPER.readValue(
                exchange.getMessage().getBody(byte[].class), SendMessageResponse.class);
        assertThat(response.getTask().history()).isEmpty();
    }

    @Test
    void sendMessageRejectsNegativeHistoryLength() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("History Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:history-agent-invalid", component, config);
        endpoint.setAgentCardSource("history-agent-invalid");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> exchange.getMessage().setBody("Done"));

        Message message = Message.builder()
                .messageId("msg-history-negative")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Invalid history")))
                .build();
        SendMessageConfiguration configuration = new SendMessageConfiguration();
        configuration.setHistoryLength(-1);
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);
        request.setConfiguration(configuration);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleSendMessage(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("InvalidParamsError");
        assertThat(error.getMessage()).contains("configuration.historyLength");
    }

    @Test
    void dispatchProcessorNegotiatesExtensionsAndPropagatesToRoute() throws Exception {
        String extensionUri = "https://example.com/a2a/ext";
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Extension Agent");
        config.setVersion("1.0.0");
        config.setValidateAuth(false);
        config.setAgentCard(agentCardWithExtension(extensionUri));

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:extension-agent", component, config);
        endpoint.setAgentCardSource("extension-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        AtomicReference<Object> routeExtensions = new AtomicReference<>();
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            routeExtensions.set(exchange.getMessage().getHeader(A2AConstants.EXTENSIONS));
            exchange.getMessage().setBody("Done");
        });

        Message message = Message.builder()
                .messageId("msg-extension")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Use extension")))
                .build();
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(A2AConstants.HEADER_A2A_EXTENSIONS, extensionUri);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        Processor dispatch = consumer.createDispatchProcessor(consumer::handleSendMessage, false);
        dispatch.process(exchange);

        assertThat(routeExtensions.get()).isEqualTo(List.of(extensionUri));
        assertThat(exchange.getMessage().getHeader(A2AConstants.HEADER_A2A_VERSION)).isEqualTo(A2AConstants.A2A_VERSION);
        assertThat(exchange.getMessage().getHeader(A2AConstants.HEADER_A2A_EXTENSIONS)).isEqualTo(extensionUri);
    }

    @Test
    void dispatchProcessorRejectsUnsupportedExtensions() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Extension Agent");
        config.setVersion("1.0.0");
        config.setValidateAuth(false);
        config.setAgentCard(agentCardWithExtension("https://example.com/a2a/supported"));

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:extension-agent-reject", component, config);
        endpoint.setAgentCardSource("extension-agent-reject");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        AtomicReference<Boolean> routeInvoked = new AtomicReference<>(false);
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> routeInvoked.set(true));

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(A2AConstants.HEADER_A2A_EXTENSIONS, "https://example.com/a2a/unsupported");
        exchange.getMessage().setBody("{}");

        Processor dispatch = consumer.createDispatchProcessor(consumer::handleSendMessage, false);
        dispatch.process(exchange);

        assertThat(routeInvoked.get()).isFalse();
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("ExtensionSupportRequiredError");
    }

    @Test
    void dispatchProcessorRejectsMissingRequiredExtension() throws Exception {
        String extensionUri = "https://example.com/a2a/required";
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Extension Agent");
        config.setVersion("1.0.0");
        config.setValidateAuth(false);
        config.setAgentCard(agentCardWithExtension(extensionUri, true));

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:extension-agent-required", component, config);
        endpoint.setAgentCardSource("extension-agent-required");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        AtomicReference<Boolean> routeInvoked = new AtomicReference<>(false);
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> routeInvoked.set(true));

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("{}");

        Processor dispatch = consumer.createDispatchProcessor(consumer::handleSendMessage, false);
        dispatch.process(exchange);

        assertThat(routeInvoked.get()).isFalse();
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("ExtensionSupportRequiredError");
        assertThat(error.getMessage()).contains(extensionUri);
    }

    @Test
    void dispatchProcessorInvokesExtensionHandlerAroundRoute() throws Exception {
        String extensionUri = "https://example.com/a2a/handled";
        StringBuilder calls = new StringBuilder();
        context.getRegistry().bind("handledExtension", new A2AExtensionHandler() {
            @Override
            public String extensionUri() {
                return extensionUri;
            }

            @Override
            public void beforeRoute(Exchange exchange, AgentExtension extension) {
                calls.append("before;");
                assertThat(extension.getUri()).isEqualTo(extensionUri);
                exchange.getMessage().setHeader("extension-before", true);
            }

            @Override
            public void afterRoute(Exchange exchange, AgentExtension extension) {
                calls.append("after;");
                assertThat(exchange.getMessage().getBody(String.class)).isEqualTo("Done");
            }
        });

        A2AConfiguration config = new A2AConfiguration();
        config.setName("Extension Agent");
        config.setVersion("1.0.0");
        config.setValidateAuth(false);
        config.setAgentCard(agentCardWithExtension(extensionUri));

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:extension-agent-handler", component, config);
        endpoint.setAgentCardSource("extension-agent-handler");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            calls.append("route;");
            assertThat(exchange.getMessage().getHeader("extension-before", Boolean.class)).isTrue();
            exchange.getMessage().setBody("Done");
        });

        Message message = Message.builder()
                .messageId("msg-extension-handler")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Use handled extension")))
                .build();
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(A2AConstants.HEADER_A2A_EXTENSIONS, extensionUri);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        Processor dispatch = consumer.createDispatchProcessor(consumer::handleSendMessage, false);
        dispatch.process(exchange);

        assertThat(calls).hasToString("before;route;after;");
        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isNull();
    }

    @Test
    void returnImmediatelyRejectedForStreaming() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Stream Agent");
        config.setVersion("1.0.0");
        config.setReturnImmediately(true);
        enableStreaming(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:stream-agent", component, config);
        endpoint.setAgentCardSource("stream-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Message message = Message.builder()
                .messageId("msg-stream")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Stream this")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleMessageStream(exchange);

        Integer httpCode = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        assertEquals(400, httpCode);

        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        A2AError error = OBJECT_MAPPER.readValue(responseBody, A2AError.class);
        assertEquals("UnsupportedOperationError", error.getCode());
    }

    @Test
    void restStreamingRequiresExplicitCapability() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Stream Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:stream-agent", component, config);
        endpoint.setAgentCardSource("stream-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Exchange exchange = createStreamMessageExchange();
        consumer.handleMessageStream(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(405);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("UnsupportedOperationError");
        assertThat(error.getMessage()).contains("Streaming is not enabled");
    }

    // ---- Push notification config tests ----

    @Test
    void handlePushConfigCreateAndGet() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Push Agent");
        config.setVersion("1.0.0");
        enablePushNotifications(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:push-agent", component, config);
        endpoint.setAgentCardSource("push-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Task task = Task.builder()
                .id("task-push")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        endpoint.getTaskStore().put("task-push", task);

        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig();
        pushConfig.setUrl("https://example.com/webhook");

        Exchange createExchange = new DefaultExchange(context);
        createExchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(pushConfig));
        createExchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-push/pushNotificationConfigs");

        consumer.handlePushConfigCreate(createExchange);

        byte[] createResponse = createExchange.getMessage().getBody(byte[].class);
        TaskPushNotificationConfig created = OBJECT_MAPPER.readValue(createResponse, TaskPushNotificationConfig.class);
        assertNotNull(created.getId());
        assertEquals("https://example.com/webhook", created.getUrl());

        Exchange getExchange = new DefaultExchange(context);
        getExchange.getMessage().setHeader(Exchange.HTTP_PATH,
                "/tasks/task-push/pushNotificationConfigs/" + created.getId());

        consumer.handlePushConfigGet(getExchange);

        byte[] getResponse = getExchange.getMessage().getBody(byte[].class);
        TaskPushNotificationConfig retrieved = OBJECT_MAPPER.readValue(getResponse, TaskPushNotificationConfig.class);
        assertEquals(created.getId(), retrieved.getId());
    }

    @Test
    void restPushConfigCreateRequiresExplicitCapability() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Push Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:push-agent", component, config);
        endpoint.setAgentCardSource("push-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();
        endpoint.getTaskStore().put("task-push", Task.builder()
                .id("task-push")
                .status(new TaskStatus(TaskState.WORKING))
                .build());

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig();
        pushConfig.setUrl("https://example.com/webhook");
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(pushConfig));
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-push/pushNotificationConfigs");

        consumer.handlePushConfigCreate(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(405);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("UnsupportedOperationError");
        assertThat(error.getMessage()).contains("Push notifications are not enabled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonRpcPushConfigCreateRequiresExplicitCapability() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Push Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:push-agent", component, config);
        endpoint.setAgentCardSource("push-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();
        endpoint.getTaskStore().put("task-push", Task.builder()
                .id("task-push")
                .status(new TaskStatus(TaskState.WORKING))
                .build());

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "jsonrpc", "2.0",
                "method", "CreateTaskPushNotificationConfig",
                "params", Map.of("taskId", "task-push", "url", "https://example.com/webhook"),
                "id", "push-disabled")));

        consumer.handleJsonRpcDispatch(exchange);

        Map<String, Object> envelope = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), Map.class);
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error.get("code")).isEqualTo(-32004);
        assertThat(error.get("message")).asString().contains("Push notifications are not enabled");
    }

    @Test
    void handlePushConfigCreateRejects404ForMissingTask() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Push Agent");
        config.setVersion("1.0.0");
        enablePushNotifications(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:push-agent", component, config);
        endpoint.setAgentCardSource("push-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig();
        pushConfig.setUrl("https://example.com/webhook");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(pushConfig));
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/nonexistent/pushNotificationConfigs");

        consumer.handlePushConfigCreate(exchange);

        Integer httpCode = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        assertEquals(404, httpCode);
    }

    @Test
    void handlePushConfigCreateValidatesWebhookUrlBeforeCustomStore() throws Exception {
        class NonValidatingTaskStore extends InMemoryTaskStore {
            private boolean putPushConfigCalled;

            @Override
            public void putPushConfig(String taskId, TaskPushNotificationConfig config) {
                putPushConfigCalled = true;
            }
        }

        NonValidatingTaskStore taskStore = new NonValidatingTaskStore();
        context.getRegistry().bind("taskStore", taskStore);

        A2AConfiguration config = new A2AConfiguration();
        config.setName("Push Agent");
        config.setVersion("1.0.0");
        enablePushNotifications(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:push-agent-custom-store", component, config);
        endpoint.setAgentCardSource("push-agent-custom-store");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Task task = Task.builder()
                .id("task-push")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        taskStore.put("task-push", task);

        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig();
        pushConfig.setUrl("http://127.0.0.1/webhook");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(pushConfig));
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-push/pushNotificationConfigs");

        consumer.handlePushConfigCreate(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("InvalidParamsError");
        assertThat(taskStore.putPushConfigCalled).isFalse();
    }

    @Test
    void handlePushConfigDelete() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Push Agent");
        config.setVersion("1.0.0");
        enablePushNotifications(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:push-agent", component, config);
        endpoint.setAgentCardSource("push-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Task task = Task.builder()
                .id("task-del")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        endpoint.getTaskStore().put("task-del", task);

        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig();
        pushConfig.setUrl("https://example.com/webhook");
        endpoint.getTaskStore().putPushConfig("task-del", pushConfig);
        String configId = pushConfig.getId();

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH,
                "/tasks/task-del/pushNotificationConfigs/" + configId);

        consumer.handlePushConfigDelete(exchange);

        byte[] responseBody = exchange.getMessage().getBody(byte[].class);
        assertNotNull(responseBody);
        assertThat(new String(responseBody)).isEqualTo("{}");
        assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class))
                .isEqualTo(A2AConstants.CONTENT_TYPE);
        assertThat(endpoint.getTaskStore().getPushConfig("task-del", configId)).isNull();
    }

    // ---- REST SSE streaming tests ----

    @Test
    void handleMessageStreamReturnsSseResponse() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Stream Agent");
        config.setVersion("1.0.0");
        enableStreaming(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:stream-agent", component, config);
        endpoint.setAgentCardSource("stream-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2ATaskStore store = endpoint.getTaskStore();
        Processor streamingProcessor = exchange -> {
            String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
            store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                    TaskStatusUpdateEvent.builder().taskId(taskId)
                            .status(new TaskStatus(TaskState.WORKING)).build()));
            store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                    TaskStatusUpdateEvent.builder().taskId(taskId)
                            .status(new TaskStatus(TaskState.COMPLETED)).build()));
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, streamingProcessor);
        consumer.doStart();

        Message message = Message.builder()
                .messageId("msg-rest-stream")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Stream via REST")))
                .build();

        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));

        consumer.handleMessageStream(exchange);

        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        assertEquals("text/event-stream", contentType);

        InputStream inputStream = exchange.getMessage().getBody(InputStream.class);
        String responseBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        assertNotNull(responseBody);

        consumer.doStop();

        String[] sseFrames = responseBody.split("\n\n");
        assertEquals(3, sseFrames.length);

        for (String frame : sseFrames) {
            assertTrue(frame.startsWith("data: "), "SSE frame must start with 'data: '");
            String json = frame.substring("data: ".length());
            StreamResponse event = OBJECT_MAPPER.readValue(json, StreamResponse.class);
            assertTrue(event.getTask() != null || event.getStatusUpdate() != null);
        }

        StreamResponse first = OBJECT_MAPPER.readValue(
                sseFrames[0].substring("data: ".length()), StreamResponse.class);
        assertNotNull(first.getTask());
        assertEquals(TaskState.SUBMITTED, first.getTask().status().state());

        StreamResponse second = OBJECT_MAPPER.readValue(
                sseFrames[1].substring("data: ".length()), StreamResponse.class);
        assertEquals(TaskState.WORKING, second.getStatusUpdate().status().state());

        StreamResponse third = OBJECT_MAPPER.readValue(
                sseFrames[2].substring("data: ".length()), StreamResponse.class);
        assertEquals(TaskState.COMPLETED, third.getStatusUpdate().status().state());
    }

    @Test
    void restSubscribeRejectsTerminalTask() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Subscribe Agent");
        config.setVersion("1.0.0");
        enableStreaming(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:subscribe-agent", component, config);
        endpoint.setAgentCardSource("subscribe-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();
        endpoint.getTaskStore().put("task-terminal", Task.builder()
                .id("task-terminal")
                .contextId("ctx-terminal")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build());

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-terminal:subscribe");
        consumer.handleTaskSubscribe(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("UnsupportedOperationError");
        assertThat(error.getMessage()).contains("already terminal");
    }

    @Test
    void restSubscribeRequiresExplicitStreamingCapability() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Subscribe Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:subscribe-agent", component, config);
        endpoint.setAgentCardSource("subscribe-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();
        endpoint.getTaskStore().put("task-active", Task.builder()
                .id("task-active")
                .contextId("ctx-active")
                .status(new TaskStatus(TaskState.WORKING))
                .build());

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-active:subscribe");
        consumer.handleTaskSubscribe(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(405);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("UnsupportedOperationError");
        assertThat(error.getMessage()).contains("Streaming is not enabled");
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonRpcSubscribeRequiresExplicitStreamingCapability() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Subscribe Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:subscribe-agent", component, config);
        endpoint.setAgentCardSource("subscribe-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();
        endpoint.getTaskStore().put("task-active", Task.builder()
                .id("task-active")
                .contextId("ctx-active")
                .status(new TaskStatus(TaskState.WORKING))
                .build());

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "jsonrpc", "2.0",
                "method", "SubscribeToTask",
                "params", Map.of("id", "task-active"),
                "id", "subscribe-disabled")));

        consumer.handleJsonRpcDispatch(exchange);

        Map<String, Object> envelope = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), Map.class);
        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertThat(error.get("code")).isEqualTo(-32004);
        assertThat(error.get("message")).asString().contains("Streaming is not enabled");
    }

    // ---- JSON-RPC streaming tests ----

    @Test
    @SuppressWarnings("unchecked")
    void handleJsonRpcDispatchStreaming() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Stream Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");
        enableStreaming(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:stream-agent", component, config);
        endpoint.setAgentCardSource("stream-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        // Processor that emits two status events via the task store subscriber mechanism.
        // executeStreamProcessing() registers a StreamSubscriber before invoking the processor,
        // so notifySubscribers dispatches to the DefaultStreamEmitter that collects the events.
        A2ATaskStore store = endpoint.getTaskStore();
        Processor streamingProcessor = exchange -> {
            String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
            store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                    TaskStatusUpdateEvent.builder().taskId(taskId)
                            .status(new TaskStatus(TaskState.WORKING)).build()));
            store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                    TaskStatusUpdateEvent.builder().taskId(taskId)
                            .status(new TaskStatus(TaskState.COMPLETED)).build()));
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, streamingProcessor);
        consumer.doStart();

        // Build JSON-RPC request for SendStreamingMessage
        Message message = Message.builder()
                .messageId("msg-stream-jrpc")
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Stream via JSON-RPC")))
                .build();

        SendMessageRequest sendReq = new SendMessageRequest();
        sendReq.setMessage(message);

        Map<String, Object> params = OBJECT_MAPPER.convertValue(sendReq, Map.class);
        Map<String, Object> jsonRpcRequest = Map.of(
                "jsonrpc", "2.0",
                "method", "SendStreamingMessage",
                "params", params,
                "id", "req-stream-1");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(jsonRpcRequest));

        consumer.handleJsonRpcDispatch(exchange);

        // Verify content-type is text/event-stream, not application/a2a+json
        String contentType = exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        assertEquals("text/event-stream", contentType);

        // Verify response body contains SSE events with JSON-RPC envelopes
        InputStream inputStream = exchange.getMessage().getBody(InputStream.class);
        String responseBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        assertNotNull(responseBody);

        consumer.doStop();

        // Split into SSE events (separated by double newline)
        String[] sseFrames = responseBody.split("\n\n");
        assertEquals(3, sseFrames.length);

        // Each frame should start with "data: " and contain a JSON-RPC envelope
        for (String frame : sseFrames) {
            assertTrue(frame.startsWith("data: "), "SSE frame must start with 'data: '");
            String jsonEnvelope = frame.substring("data: ".length());
            Map<String, Object> envelope = OBJECT_MAPPER.readValue(jsonEnvelope, Map.class);
            assertEquals("2.0", envelope.get("jsonrpc"));
            assertEquals("req-stream-1", envelope.get("id"));
            assertNotNull(envelope.get("result"));

            // The result should be a valid StreamResponse
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            assertTrue(result.containsKey("task") || result.containsKey("statusUpdate"));
        }

        // Verify first event is the submitted Task, then route-generated WORKING and COMPLETED events
        String firstJson = sseFrames[0].substring("data: ".length());
        Map<String, Object> first = OBJECT_MAPPER.readValue(firstJson, Map.class);
        Map<String, Object> firstResult = (Map<String, Object>) first.get("result");
        StreamResponse firstEvent = OBJECT_MAPPER.convertValue(firstResult, StreamResponse.class);
        assertNotNull(firstEvent.getTask());
        assertEquals(TaskState.SUBMITTED, firstEvent.getTask().status().state());

        String secondJson = sseFrames[1].substring("data: ".length());
        Map<String, Object> second = OBJECT_MAPPER.readValue(secondJson, Map.class);
        Map<String, Object> secondResult = (Map<String, Object>) second.get("result");
        StreamResponse secondEvent = OBJECT_MAPPER.convertValue(secondResult, StreamResponse.class);
        assertEquals(TaskState.WORKING, secondEvent.getStatusUpdate().status().state());

        String thirdJson = sseFrames[2].substring("data: ".length());
        Map<String, Object> third = OBJECT_MAPPER.readValue(thirdJson, Map.class);
        Map<String, Object> thirdResult = (Map<String, Object>) third.get("result");
        StreamResponse thirdEvent = OBJECT_MAPPER.convertValue(thirdResult, StreamResponse.class);
        assertEquals(TaskState.COMPLETED, thirdEvent.getStatusUpdate().status().state());
    }

    // ---- Capacity limiting tests ----

    private static Task listTask(String id, String contextId, TaskState state, OffsetDateTime timestamp) {
        return Task.builder()
                .id(id)
                .contextId(contextId)
                .status(new TaskStatus(state, null, timestamp))
                .artifacts(List.of(Artifact.builder()
                        .artifactId(id + "-artifact")
                        .parts(List.of(new TextPart("artifact")))
                        .build()))
                .history(List.of(
                        Message.builder()
                                .messageId(id + "-history-1")
                                .role(Message.Role.ROLE_AGENT)
                                .parts(List.of(new TextPart("first")))
                                .build(),
                        Message.builder()
                                .messageId(id + "-history-2")
                                .role(Message.Role.ROLE_AGENT)
                                .parts(List.of(new TextPart("second")))
                                .build()))
                .build();
    }

    private static AgentCard agentCardWithExtension(String extensionUri) {
        return agentCardWithExtension(extensionUri, false);
    }

    private static AgentCard agentCardWithExtension(String extensionUri, boolean required) {
        AgentExtension extension = new AgentExtension();
        extension.setUri(extensionUri);
        extension.setRequired(required);
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setExtensions(List.of(extension));
        return AgentCard.builder()
                .setName("Extension Agent")
                .setVersion("1.0.0")
                .setCapabilities(capabilities)
                .build();
    }

    private A2AConsumer createCapacityLimitedConsumer(
            int maxConcurrentTasks, int taskQueueSize, boolean returnImmediately,
            Processor processor)
            throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Capacity Agent");
        config.setVersion("1.0.0");
        config.setMaxConcurrentTasks(maxConcurrentTasks);
        config.setTaskQueueSize(taskQueueSize);
        config.setReturnImmediately(returnImmediately);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:capacity-agent", component, config);
        endpoint.setAgentCardSource("capacity-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2AConsumer consumer = new A2AConsumer(endpoint, processor);
        consumer.doStart();
        return consumer;
    }

    private Exchange createSendMessageExchange() {
        Exchange exchange = new DefaultExchange(context);
        String body = "{\"message\":{\"messageId\":\"msg-" + UUID.randomUUID()
                      + "\",\"role\":\"user\",\"parts\":[{\"text\":\"Hello\"}]}}";
        exchange.getMessage().setBody(body);
        return exchange;
    }

    private Exchange createStreamMessageExchange() {
        Exchange exchange = new DefaultExchange(context);
        Message message = Message.builder()
                .messageId("msg-" + UUID.randomUUID())
                .role(Message.Role.ROLE_USER)
                .parts(List.of(new TextPart("Hello")))
                .build();
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);
        try {
            exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return exchange;
    }

    private static ExecutorService replaceAsyncExecutor(A2AConsumer consumer, ExecutorService replacement) throws Exception {
        Field field = A2AConsumer.class.getDeclaredField("asyncExecutor");
        field.setAccessible(true);
        ExecutorService previous = (ExecutorService) field.get(consumer);
        field.set(consumer, replacement);
        return previous;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> taskOwners(A2AConsumer consumer) throws Exception {
        Field field = A2AConsumer.class.getDeclaredField("taskOwners");
        field.setAccessible(true);
        return (Map<String, String>) field.get(consumer);
    }

    @Test
    void syncPathRejectsWhenAtCapacity() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(1);

        Processor slowProcessor = exchange -> {
            startedLatch.countDown();
            blockLatch.await(10, TimeUnit.SECONDS);
        };

        A2AConsumer consumer = createCapacityLimitedConsumer(1, 0, false, slowProcessor);

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            // First request occupies the single permit
            Future<?> first = exec.submit(() -> {
                try {
                    consumer.handleSendMessage(createSendMessageExchange());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertTrue(startedLatch.await(5, TimeUnit.SECONDS));

            // Second request should be rejected
            Exchange second = createSendMessageExchange();
            consumer.handleSendMessage(second);

            Integer httpCode = second.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            assertEquals(429, httpCode);

            A2AError error = OBJECT_MAPPER.readValue(second.getMessage().getBody(byte[].class), A2AError.class);
            assertEquals("ServerBusyError", error.getCode());
            assertThat(error.getMessage()).contains("1 concurrent tasks");
        } finally {
            blockLatch.countDown();
            exec.shutdownNow();
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void unlimitedCapacityAllowsAllRequests() throws Exception {
        A2AConsumer consumer = createCapacityLimitedConsumer(0, 0, false, exchange -> {
            SendMessageResponse response = new SendMessageResponse();
            Task task = Task.builder()
                    .id("t1")
                    .status(new TaskStatus(TaskState.COMPLETED))
                    .build();
            response.setTask(task);
            exchange.getMessage().setBody(response);
        });

        try {
            for (int i = 0; i < 5; i++) {
                Exchange ex = createSendMessageExchange();
                consumer.handleSendMessage(ex);
                assertThat(ex.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isNull();
            }
        } finally {
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void asyncPathQueuesWhenAtCapacity() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);

        A2AConsumer consumer = createCapacityLimitedConsumer(1, 1, true, exchange -> {
            blockLatch.await(10, TimeUnit.SECONDS);
        });

        try {
            // First async request occupies the permit
            Exchange first = createSendMessageExchange();
            consumer.handleSendMessage(first);
            SendMessageResponse firstResponse
                    = OBJECT_MAPPER.readValue(first.getMessage().getBody(byte[].class), SendMessageResponse.class);
            assertEquals(TaskState.SUBMITTED, firstResponse.getTask().status().state());

            // Second async request should be queued (not rejected)
            Exchange second = createSendMessageExchange();
            consumer.handleSendMessage(second);
            SendMessageResponse secondResponse
                    = OBJECT_MAPPER.readValue(second.getMessage().getBody(byte[].class), SendMessageResponse.class);
            assertEquals(TaskState.SUBMITTED, secondResponse.getTask().status().state());
            assertThat(second.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isNull();

            // Release the first task — queued task should drain
            blockLatch.countDown();

            // Both tasks should eventually complete
            String taskId1 = firstResponse.getTask().id();
            String taskId2 = secondResponse.getTask().id();
            await()
                    .atMost(10, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        Task t1 = consumer.getEndpoint().getTaskStore().get(taskId1);
                        Task t2 = consumer.getEndpoint().getTaskStore().get(taskId2);
                        assertNotNull(t1);
                        assertNotNull(t2);
                        assertTrue(t1.status().state().isTerminal());
                        assertTrue(t2.status().state().isTerminal());
                    });
        } finally {
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void asyncPathRejectsWhenQueueFull() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);

        A2AConsumer consumer = createCapacityLimitedConsumer(1, 1, true, exchange -> {
            blockLatch.await(10, TimeUnit.SECONDS);
        });

        try {
            // First occupies permit
            consumer.handleSendMessage(createSendMessageExchange());
            // Second fills queue
            consumer.handleSendMessage(createSendMessageExchange());
            // Third should be rejected
            Exchange third = createSendMessageExchange();
            consumer.handleSendMessage(third);

            Integer httpCode = third.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            assertEquals(429, httpCode);

            A2AError error = OBJECT_MAPPER.readValue(third.getMessage().getBody(byte[].class), A2AError.class);
            assertEquals("ServerBusyError", error.getCode());
        } finally {
            blockLatch.countDown();
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void asyncPathRejectsImmediatelyWithNoQueue() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);

        A2AConsumer consumer = createCapacityLimitedConsumer(1, 0, true, exchange -> {
            blockLatch.await(10, TimeUnit.SECONDS);
        });

        try {
            // First occupies permit
            consumer.handleSendMessage(createSendMessageExchange());
            // Second should be rejected (no queue)
            Exchange second = createSendMessageExchange();
            consumer.handleSendMessage(second);

            Integer httpCode = second.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
            assertEquals(429, httpCode);
        } finally {
            blockLatch.countDown();
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void serverBusyErrorFormatAndHttpCode() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(2);

        A2AConsumer consumer = createCapacityLimitedConsumer(2, 0, false, exchange -> {
            startedLatch.countDown();
            blockLatch.await(10, TimeUnit.SECONDS);
        });

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            // Occupy both permits
            exec.submit(() -> {
                try {
                    consumer.handleSendMessage(createSendMessageExchange());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            exec.submit(() -> {
                try {
                    consumer.handleSendMessage(createSendMessageExchange());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            assertTrue(startedLatch.await(5, TimeUnit.SECONDS));

            // Third should be rejected
            Exchange rejected = createSendMessageExchange();
            consumer.handleSendMessage(rejected);

            assertEquals(429, rejected.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
            A2AError error = OBJECT_MAPPER.readValue(rejected.getMessage().getBody(byte[].class), A2AError.class);
            assertEquals("ServerBusyError", error.getCode());
            assertThat(error.getMessage()).contains("2 concurrent tasks");
        } finally {
            blockLatch.countDown();
            exec.shutdownNow();
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonRpcStreamMalformedParamsDoesNotLeakPermit() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Permit Test Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");
        config.setMaxConcurrentTasks(2);
        enableStreaming(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:permit-test", component, config);
        endpoint.setAgentCardSource("permit-test");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2ATaskStore store = endpoint.getTaskStore();
        Processor streamingProcessor = exchange -> {
            String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
            store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                    TaskStatusUpdateEvent.builder().taskId(taskId)
                            .status(new TaskStatus(TaskState.COMPLETED)).build()));
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, streamingProcessor);
        consumer.doStart();

        try {
            // Send malformed JSON-RPC streaming request (params missing "message" field)
            Exchange malformed = new DefaultExchange(context);
            Map<String, Object> malformedRpc = Map.of(
                    "jsonrpc", "2.0", "id", 1,
                    "method", "SendStreamingMessage",
                    "params", Map.of("notMessage", "bad"));
            malformed.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(malformedRpc));
            consumer.handleJsonRpcDispatch(malformed);

            // Should get a JSON-RPC error response
            byte[] body = malformed.getMessage().getBody(byte[].class);
            assertNotNull(body);
            Map<String, Object> response = OBJECT_MAPPER.readValue(body, Map.class);
            assertNotNull(response.get("error"), "Expected JSON-RPC error for malformed params");

            // Now send 2 valid streaming requests — both must succeed (permits not leaked)
            for (int i = 0; i < 2; i++) {
                Exchange valid = new DefaultExchange(context);
                Message message = Message.builder()
                        .messageId("msg-" + i)
                        .role(Message.Role.ROLE_USER)
                        .parts(List.of(new TextPart("Hello " + i)))
                        .build();
                SendMessageRequest request = new SendMessageRequest();
                request.setMessage(message);
                Map<String, Object> params = OBJECT_MAPPER.convertValue(request, Map.class);
                Map<String, Object> validRpc = Map.of(
                        "jsonrpc", "2.0", "id", i + 2,
                        "method", "SendStreamingMessage",
                        "params", params);
                valid.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(validRpc));
                consumer.handleJsonRpcDispatch(valid);

                String contentType = valid.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
                assertEquals("text/event-stream", contentType,
                        "Request " + i + " should succeed, not be rejected for capacity");
            }
        } finally {
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void cancelInFlightTaskReleasesPermit() throws Exception {
        CountDownLatch blockLatch = new CountDownLatch(1);
        CountDownLatch startedLatch = new CountDownLatch(1);

        A2AConsumer consumer = createCapacityLimitedConsumer(1, 0, true, exchange -> {
            startedLatch.countDown();
            blockLatch.await(30, TimeUnit.SECONDS);
        });

        try {
            // Start an async task that blocks
            Exchange first = createSendMessageExchange();
            consumer.handleSendMessage(first);
            SendMessageResponse firstResponse
                    = OBJECT_MAPPER.readValue(first.getMessage().getBody(byte[].class), SendMessageResponse.class);
            String taskId = firstResponse.getTask().id();

            // Wait for the task to start processing
            assertTrue(startedLatch.await(5, TimeUnit.SECONDS));

            // Cancel the task
            Exchange cancelExchange = new DefaultExchange(context);
            cancelExchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/" + taskId + ":cancel");
            consumer.handleCancelTask(cancelExchange);

            // Verify it was cancelled
            byte[] cancelBody = cancelExchange.getMessage().getBody(byte[].class);
            Task cancelledTask = OBJECT_MAPPER.readValue(cancelBody, Task.class);
            assertEquals(TaskState.CANCELED, cancelledTask.status().state());

            // The permit should now be available — a new async task should be accepted
            await()
                    .atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        Exchange second = createSendMessageExchange();
                        consumer.handleSendMessage(second);
                        Integer httpCode = second.getMessage().getHeader(
                                Exchange.HTTP_RESPONSE_CODE, Integer.class);
                        assertThat(httpCode).satisfiesAnyOf(
                                code -> assertThat(code).isNull(),
                                code -> assertThat(code).isNotEqualTo(429));
                    });
        } finally {
            blockLatch.countDown();
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void restStreamSetupExceptionDoesNotLeakPermit() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Permit Test Agent");
        config.setVersion("1.0.0");
        config.setMaxConcurrentTasks(2);
        enableStreaming(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:permit-test-rest", component, config);
        endpoint.setAgentCardSource("permit-test-rest");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2ATaskStore store = endpoint.getTaskStore();
        Processor streamingProcessor = exchange -> {
            String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
            store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                    TaskStatusUpdateEvent.builder().taskId(taskId)
                            .status(new TaskStatus(TaskState.COMPLETED)).build()));
        };

        A2AConsumer consumer = new A2AConsumer(endpoint, streamingProcessor);
        consumer.doStart();

        try {
            // Reject a request with a null message before acquiring a permit.
            Exchange bad = new DefaultExchange(context);
            bad.getMessage().setBody("{\"message\":null}");

            consumer.handleMessageStream(bad);
            assertEquals(400, bad.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));

            // Both permits should still be available
            for (int i = 0; i < 2; i++) {
                Exchange valid = new DefaultExchange(context);
                Message message = Message.builder()
                        .messageId("msg-" + i)
                        .role(Message.Role.ROLE_USER)
                        .parts(List.of(new TextPart("Hello " + i)))
                        .build();
                SendMessageRequest request = new SendMessageRequest();
                request.setMessage(message);
                valid.getMessage().setBody(OBJECT_MAPPER.writeValueAsString(request));
                consumer.handleMessageStream(valid);

                String contentType = valid.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
                assertEquals("text/event-stream", contentType,
                        "Request " + i + " should succeed — permit must not have leaked");
            }
        } finally {
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void asyncExecutorRejectionCleansTaskAndPermit() throws Exception {
        A2AConsumer consumer = createCapacityLimitedConsumer(1, 0, true,
                exchange -> exchange.getMessage().setBody("Done"));
        ExecutorService rejecting = Executors.newSingleThreadExecutor();
        rejecting.shutdownNow();
        ExecutorService original = replaceAsyncExecutor(consumer, rejecting);
        context.getExecutorServiceManager().shutdownNow(original);

        try {
            assertThrows(RuntimeException.class, () -> consumer.handleSendMessage(createSendMessageExchange()));
            assertThat(consumer.getEndpoint().getTaskStore().keys()).isEmpty();

            replaceAsyncExecutor(consumer, Executors.newSingleThreadExecutor());
            Exchange accepted = createSendMessageExchange();
            consumer.handleSendMessage(accepted);

            assertThat(accepted.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isNull();
            SendMessageResponse response = OBJECT_MAPPER.readValue(
                    accepted.getMessage().getBody(byte[].class), SendMessageResponse.class);
            assertThat(response.getTask().status().state()).isEqualTo(TaskState.SUBMITTED);
        } finally {
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void restStreamExecutorRejectionCleansTaskAndPermit() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Stream Rejection Agent");
        config.setVersion("1.0.0");
        config.setMaxConcurrentTasks(1);
        enableStreaming(config);

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:stream-rejection", component, config);
        endpoint.setAgentCardSource("stream-rejection");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        A2ATaskStore store = endpoint.getTaskStore();
        Processor streamingProcessor = exchange -> {
            String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
            store.notifySubscribers(taskId, StreamResponse.ofStatusUpdate(
                    TaskStatusUpdateEvent.builder().taskId(taskId)
                            .status(new TaskStatus(TaskState.COMPLETED)).build()));
        };
        A2AConsumer consumer = new A2AConsumer(endpoint, streamingProcessor);
        consumer.doStart();

        ExecutorService rejecting = Executors.newSingleThreadExecutor();
        rejecting.shutdownNow();
        ExecutorService original = replaceAsyncExecutor(consumer, rejecting);
        context.getExecutorServiceManager().shutdownNow(original);

        try {
            assertThrows(RuntimeException.class, () -> consumer.handleMessageStream(createStreamMessageExchange()));
            assertThat(store.keys()).isEmpty();

            replaceAsyncExecutor(consumer, Executors.newSingleThreadExecutor());
            Exchange accepted = createStreamMessageExchange();
            consumer.handleMessageStream(accepted);

            assertThat(accepted.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class))
                    .isEqualTo(A2AConstants.SSE_CONTENT_TYPE);
            accepted.getMessage().getBody(InputStream.class).close();
        } finally {
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    @Test
    void jsonRpcDispatchUsesApplicationJsonForSuccess() throws Exception {
        A2AConsumer consumer = createJsonRpcConsumer(exchange -> {
        });
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "jsonrpc", "2.0",
                "method", "ListTasks",
                "params", Map.of(),
                "id", "json-media")));

        consumer.handleJsonRpcDispatch(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class))
                .isEqualTo(A2AConstants.JSONRPC_CONTENT_TYPE);
        JsonNode envelope = OBJECT_MAPPER.readTree(exchange.getMessage().getBody(byte[].class));
        assertThat(envelope.get("id").asText()).isEqualTo("json-media");
        assertThat(envelope.has("result")).isTrue();
    }

    @Test
    void jsonRpcPreDispatchVersionErrorUsesJsonRpcEnvelopeAndPreservesId() throws Exception {
        A2AConsumer consumer = createJsonRpcConsumer(exchange -> {
        });
        Processor dispatch = consumer.createDispatchProcessor(consumer::handleJsonRpcDispatch, false);
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(A2AConstants.HEADER_A2A_VERSION, "2.0");
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "jsonrpc", "2.0",
                "method", "ListTasks",
                "params", Map.of(),
                "id", "version-1")));

        dispatch.process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class))
                .isEqualTo(A2AConstants.JSONRPC_CONTENT_TYPE);
        JsonNode envelope = OBJECT_MAPPER.readTree(exchange.getMessage().getBody(byte[].class));
        assertThat(envelope.get("id").asText()).isEqualTo("version-1");
        assertThat(envelope.get("error").get("code").asInt()).isEqualTo(-32009);
        assertThat(envelope.get("error").get("data").get(0).get("reason").asText())
                .isEqualTo("VERSION_NOT_SUPPORTED");
    }

    @Test
    void jsonRpcMethodWithNonStringTypeReturnsInvalidRequestAndPreservesId() throws Exception {
        A2AConsumer consumer = createJsonRpcConsumer(exchange -> {
        });
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(Map.of(
                "jsonrpc", "2.0",
                "method", 42,
                "params", Map.of(),
                "id", "bad-method-type")));

        consumer.handleJsonRpcDispatch(exchange);

        JsonNode envelope = OBJECT_MAPPER.readTree(exchange.getMessage().getBody(byte[].class));
        assertThat(envelope.get("id").asText()).isEqualTo("bad-method-type");
        assertThat(envelope.get("error").get("code").asInt()).isEqualTo(-32600);
    }

    @Test
    void restVersionNegotiationUsesHeaderBeforeQueryAndRejectsBlankAsLegacy03() throws Exception {
        A2AConsumer consumer = createRestConsumer(exchange -> exchange.getMessage().setBody("ok"));
        Processor dispatch = consumer.createDispatchProcessor(exchange -> {
            exchange.getMessage().setBody(OBJECT_MAPPER.writeValueAsBytes(Map.of("ok", true)));
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, A2AConstants.CONTENT_TYPE);
        }, false);

        Exchange headerWins = new DefaultExchange(context);
        headerWins.getMessage().setHeader(A2AConstants.HEADER_A2A_VERSION, A2AConstants.A2A_VERSION);
        headerWins.getMessage().setHeader(Exchange.HTTP_QUERY, "A2A-Version=2.0");
        dispatch.process(headerWins);
        assertThat(headerWins.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isNull();

        Exchange blankHeader = new DefaultExchange(context);
        blankHeader.getMessage().setHeader(A2AConstants.HEADER_A2A_VERSION, "");
        dispatch.process(blankHeader);

        assertThat(blankHeader.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(blankHeader.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("VersionNotSupportedError");
        assertThat(error.getMessage()).contains("0.3");
    }

    @Test
    void restVersionQueryParameterIsRejectedWhenUnsupported() throws Exception {
        A2AConsumer consumer = createRestConsumer(exchange -> exchange.getMessage().setBody("ok"));
        Processor dispatch = consumer.createDispatchProcessor(consumer::handleSendMessage, false);
        Exchange exchange = createSendMessageExchange();
        exchange.getMessage().setHeader(Exchange.HTTP_QUERY, "A2A-Version=2.0");

        dispatch.process(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("VersionNotSupportedError");
    }

    @Test
    void malformedRestJsonReturnsA2AInvalidParamsError() throws Exception {
        A2AConsumer consumer = createRestConsumer(exchange -> {
            throw new AssertionError("Route should not be reached for malformed JSON");
        });
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody("{not-json");

        consumer.handleSendMessage(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(400);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("InvalidParamsError");
        assertThat(error.getMessage()).contains("Malformed JSON request");
    }

    @Test
    void restInputStreamBodyIsBoundedBeforeMaterialization() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Bounded Body Agent");
        config.setVersion("1.0.0");
        config.setValidateAuth(false);
        config.setMaxPayloadSize(16);

        A2AConsumer consumer = new A2AConsumer(
                createEndpoint("a2a:bounded-body", "bounded-body", config),
                exchange -> {
                    throw new AssertionError("Route should not be reached for oversized input stream");
                });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setBody(new ByteArrayInputStream("{\"message\":\"too large\"}".getBytes(StandardCharsets.UTF_8)));

        consumer.handleSendMessage(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(413);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("ContentTypeNotSupportedError");
    }

    @Test
    void missingTaskLookupCleansRememberedOwner() throws Exception {
        A2AConsumer consumer = createRestConsumer(exchange -> {
        });
        taskOwners(consumer).put("missing-task", "subject:alice");

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/missing-task");
        consumer.handleGetTask(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(404);
        assertThat(taskOwners(consumer)).doesNotContainKey("missing-task");
    }

    @Test
    void listTasksCleansOwnersForExpiredStoreEntries() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Owner Cleanup Agent");
        config.setVersion("1.0.0");
        config.setValidateAuth(false);
        config.setCompletedTaskTtl(0);

        A2AConsumer consumer = new A2AConsumer(
                createEndpoint("a2a:owner-cleanup", "owner-cleanup", config),
                exchange -> {
                });
        consumer.getEndpoint().getTaskStore().put("expired-owned-task", Task.builder()
                .id("expired-owned-task")
                .contextId("ctx-owner-cleanup")
                .status(new TaskStatus(
                        TaskState.COMPLETED, null, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(5)))
                .build());
        taskOwners(consumer).put("expired-owned-task", "subject:alice");

        Exchange exchange = new DefaultExchange(context);
        consumer.handleListTasks(exchange);

        assertThat(taskOwners(consumer)).doesNotContainKey("expired-owned-task");
    }

    @Test
    void pushConfigCreateAppliesPayloadLimit() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Push Size Agent");
        config.setVersion("1.0.0");
        config.setMaxPayloadSize(32);
        enablePushNotifications(config);

        A2AEndpoint endpoint = createEndpoint("a2a:push-size", "push-size", config);
        endpoint.getTaskStore().put("task-push", Task.builder()
                .id("task-push")
                .contextId("ctx-push")
                .status(new TaskStatus(TaskState.WORKING))
                .build());
        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/tasks/task-push/pushNotificationConfigs");
        exchange.getMessage().setBody("{\"url\":\"https://example.com/" + "x".repeat(64) + "\"}");

        consumer.handlePushConfigCreate(exchange);

        assertThat(exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class)).isEqualTo(413);
        A2AError error = OBJECT_MAPPER.readValue(exchange.getMessage().getBody(byte[].class), A2AError.class);
        assertThat(error.getCode()).isEqualTo("ContentTypeNotSupportedError");
    }

    @Test
    void restPathIdsAreUrlDecodedAndBasePathIsNormalized() throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Decode Agent");
        config.setVersion("1.0.0");
        config.setBasePath("api/a2a/");
        enablePushNotifications(config);

        A2AEndpoint endpoint = createEndpoint("a2a:decode", "decode", config);
        endpoint.getTaskStore().put("task 1", Task.builder()
                .id("task 1")
                .contextId("ctx-decode")
                .status(new TaskStatus(TaskState.WORKING))
                .build());
        endpoint.getTaskStore().put("task+1", Task.builder()
                .id("task+1")
                .contextId("ctx-decode-plus")
                .status(new TaskStatus(TaskState.WORKING))
                .build());
        TaskPushNotificationConfig pushConfig = new TaskPushNotificationConfig();
        pushConfig.setId("config 1");
        pushConfig.setUrl("https://example.com/webhook");
        endpoint.getTaskStore().putPushConfig("task 1", pushConfig);

        A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
        });

        Exchange getTask = new DefaultExchange(context);
        getTask.getMessage().setHeader(Exchange.HTTP_PATH, "/api/a2a/tasks/task%201");
        consumer.handleGetTask(getTask);
        Task task = OBJECT_MAPPER.readValue(getTask.getMessage().getBody(byte[].class), Task.class);
        assertThat(task.id()).isEqualTo("task 1");

        Exchange getTaskWithPlus = new DefaultExchange(context);
        getTaskWithPlus.getMessage().setHeader(Exchange.HTTP_PATH, "/api/a2a/tasks/task+1");
        consumer.handleGetTask(getTaskWithPlus);
        Task taskWithPlus = OBJECT_MAPPER.readValue(getTaskWithPlus.getMessage().getBody(byte[].class), Task.class);
        assertThat(taskWithPlus.id()).isEqualTo("task+1");

        Exchange getConfig = new DefaultExchange(context);
        getConfig.getMessage().setHeader(Exchange.HTTP_PATH,
                "/api/a2a/tasks/task%201/pushNotificationConfigs/config%201");
        consumer.handlePushConfigGet(getConfig);
        TaskPushNotificationConfig response = OBJECT_MAPPER.readValue(
                getConfig.getMessage().getBody(byte[].class), TaskPushNotificationConfig.class);
        assertThat(response.getId()).isEqualTo("config 1");
        assertThat(config.getBasePath()).isEqualTo("/api/a2a");
    }

    @Test
    void consumerStartupFailsWhenConfiguredHttpServerComponentIsNotRestConsumerFactory() throws Exception {
        DefaultCamelContext isolated = new DefaultCamelContext();
        A2AEndpoint endpoint = null;
        try {
            isolated.getRegistry().bind("notRestFactory", new Object());
            isolated.start();
            A2AConfiguration config = new A2AConfiguration();
            config.setName("No Transport Agent");
            config.setVersion("1.0.0");
            config.setHttpServerComponent("notRestFactory");

            A2AComponent component = new A2AComponent();
            component.setCamelContext(isolated);
            endpoint = new A2AEndpoint("a2a:no-transport", component, config);
            endpoint.setAgentCardSource("no-transport");
            endpoint.setCamelContext(isolated);
            endpoint.doStart();

            A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            });

            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, consumer::doStart);
            assertThat(error.getMessage()).contains("does not implement RestConsumerFactory");
        } finally {
            if (endpoint != null) {
                endpoint.doStop();
            }
            isolated.stop();
        }
    }

    @Test
    void consumerStartupFailsWhenRestConsumerFactoryDiscoveryIsAmbiguous() throws Exception {
        DefaultCamelContext isolated = new DefaultCamelContext();
        A2AEndpoint endpoint = null;
        try {
            isolated.getRegistry().bind("firstFactory", new NoopRestConsumerFactory());
            isolated.getRegistry().bind("secondFactory", new NoopRestConsumerFactory());
            isolated.start();

            A2AConfiguration config = new A2AConfiguration();
            config.setName("Ambiguous Transport Agent");
            config.setVersion("1.0.0");

            A2AComponent component = new A2AComponent();
            component.setCamelContext(isolated);
            endpoint = new A2AEndpoint("a2a:ambiguous-transport", component, config);
            endpoint.setAgentCardSource("ambiguous-transport");
            endpoint.setCamelContext(isolated);
            endpoint.doStart();

            A2AConsumer consumer = new A2AConsumer(endpoint, exchange -> {
            });

            IllegalStateException error = assertThrows(IllegalStateException.class, consumer::doStart);
            assertThat(error.getMessage()).contains("No RestConsumerFactory found");
        } finally {
            if (endpoint != null) {
                endpoint.doStop();
            }
            isolated.stop();
        }
    }

    @Test
    void consumerRegistersBindingSpecificMediaTypes() throws Exception {
        NoopRestConsumerFactory factory
                = (NoopRestConsumerFactory) context.getRegistry().lookupByName("noopRestConsumerFactory");
        A2AConsumer restConsumer = createRestConsumer(exchange -> {
        });

        restConsumer.doStart();
        try {
            assertThat(factory.registrations())
                    .anySatisfy(registration -> {
                        assertThat(registration.verb()).isEqualTo("POST");
                        assertThat(registration.path()).isEqualTo("/message:send");
                        assertThat(registration.consumes()).isEqualTo(A2AConstants.CONTENT_TYPE);
                        assertThat(registration.produces()).isEqualTo(A2AConstants.CONTENT_TYPE);
                    })
                    .anySatisfy(registration -> {
                        assertThat(registration.path()).isEqualTo("/message:stream");
                        assertThat(registration.consumes()).isEqualTo(A2AConstants.CONTENT_TYPE);
                        assertThat(registration.produces())
                                .isEqualTo(A2AConstants.CONTENT_TYPE + "," + A2AConstants.SSE_CONTENT_TYPE);
                    });
        } finally {
            restConsumer.doStop();
            restConsumer.doShutdown();
        }

        factory.registrations().clear();
        A2AConsumer jsonRpcConsumer = createJsonRpcConsumer(exchange -> {
        });
        jsonRpcConsumer.doStart();
        try {
            assertThat(factory.registrations())
                    .anySatisfy(registration -> {
                        assertThat(registration.verb()).isEqualTo("POST");
                        assertThat(registration.path()).isEqualTo("/");
                        assertThat(registration.consumes()).isEqualTo(A2AConstants.JSONRPC_CONTENT_TYPE);
                        assertThat(registration.produces())
                                .isEqualTo(A2AConstants.JSONRPC_CONTENT_TYPE + "," + A2AConstants.SSE_CONTENT_TYPE);
                        assertThat(registration.parameters()).containsEntry("useStreaming", "true");
                    });
        } finally {
            jsonRpcConsumer.doStop();
            jsonRpcConsumer.doShutdown();
        }
    }

    @Test
    void nestedHttpConsumersUseEndpointConsumerConfiguration() throws Exception {
        NoopRestConsumerFactory factory
                = (NoopRestConsumerFactory) context.getRegistry().lookupByName("noopRestConsumerFactory");
        factory.registrations().clear();
        A2AConsumer consumer = createRestConsumer(exchange -> {
        });
        consumer.getEndpoint().setBridgeErrorHandler(true);

        consumer.doStart();
        try {
            assertThat(factory.registrations()).isNotEmpty();
            assertThat(factory.registrations()).allSatisfy(registration -> {
                assertThat(registration.consumer()).isInstanceOf(DefaultConsumer.class);
                DefaultConsumer nestedConsumer = (DefaultConsumer) registration.consumer();
                assertThat(nestedConsumer.getExceptionHandler())
                        .isInstanceOf(BridgeExceptionHandlerToErrorHandler.class);
            });
        } finally {
            consumer.doStop();
            consumer.doShutdown();
        }
    }

    private A2AConsumer createRestConsumer(Processor processor) throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("REST Phase 3 Agent");
        config.setVersion("1.0.0");
        config.setValidateAuth(false);
        return new A2AConsumer(createEndpoint("a2a:rest-phase3", "rest-phase3", config), processor);
    }

    private A2AConsumer createJsonRpcConsumer(Processor processor) throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("JSON-RPC Phase 3 Agent");
        config.setVersion("1.0.0");
        config.setProtocolBinding("jsonrpc");
        config.setValidateAuth(false);
        return new A2AConsumer(createEndpoint("a2a:jsonrpc-phase3", "jsonrpc-phase3", config), processor);
    }

    private A2AEndpoint createEndpoint(String uri, String cardSource, A2AConfiguration config) throws Exception {
        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);
        A2AEndpoint endpoint = new A2AEndpoint(uri, component, config);
        endpoint.setAgentCardSource(cardSource);
        endpoint.setCamelContext(context);
        endpoint.doStart();
        return endpoint;
    }
}
