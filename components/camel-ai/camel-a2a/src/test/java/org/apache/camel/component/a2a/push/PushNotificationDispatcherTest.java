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
package org.apache.camel.component.a2a.push;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.component.a2a.model.AuthenticationInfo;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.TaskPushNotificationConfig;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.state.InMemoryTaskStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class PushNotificationDispatcherTest {

    private HttpServer mockServer;
    private int port;
    private InMemoryTaskStore store;
    private PushNotificationDispatcher dispatcher;
    private ScheduledExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = mockServer.getAddress().getPort();
        store = new InMemoryTaskStore();
        store.setAllowLocalWebhookUrls(true);
        executor = Executors.newScheduledThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    @Test
    void dispatchPostsEventToWebhook() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedAuth = new AtomicReference<>();

        mockServer.createContext("/webhook", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes()));
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            latch.countDown();
        });
        mockServer.start();

        dispatcher = new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 0, 1000, executor, true);

        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("http://localhost:" + port + "/webhook");
        config.setAuthentication(new AuthenticationInfo("Bearer", "secret-token"));
        store.putPushConfig("task-1", config);

        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        dispatcher.dispatch("task-1", StreamResponse.ofStatusUpdate(event));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedBody.get()).contains("TASK_STATE_COMPLETED");
        assertThat(receivedAuth.get()).isEqualTo("Bearer secret-token");
    }

    @Test
    void dispatchRetriesOnServerError() throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);

        mockServer.createContext("/webhook", exchange -> {
            int count = requestCount.incrementAndGet();
            if (count <= 2) {
                exchange.sendResponseHeaders(500, -1);
            } else {
                exchange.sendResponseHeaders(200, -1);
                latch.countDown();
            }
        });
        mockServer.start();

        dispatcher = new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 3, 50, executor, true);

        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("http://localhost:" + port + "/webhook");
        store.putPushConfig("task-1", config);

        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        dispatcher.dispatch("task-1", StreamResponse.ofStatusUpdate(event));

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(requestCount.get()).isEqualTo(3);
    }

    @Test
    void dispatchDeletesConfigOn410() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        mockServer.createContext("/webhook", exchange -> {
            exchange.sendResponseHeaders(410, -1);
            latch.countDown();
        });
        mockServer.start();

        dispatcher = new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 0, 1000, executor, true);

        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("http://localhost:" + port + "/webhook");
        store.putPushConfig("task-1", config);
        String configId = config.getId();

        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        dispatcher.dispatch("task-1", StreamResponse.ofStatusUpdate(event));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(store.getPushConfig("task-1", configId)).isNull());
    }

    @Test
    void dispatchNoRetryOnClientError() throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);

        mockServer.createContext("/webhook", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(400, -1);
        });
        mockServer.start();

        dispatcher = new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 3, 50, executor, true);

        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("http://localhost:" + port + "/webhook");
        store.putPushConfig("task-1", config);

        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        dispatcher.dispatch("task-1", StreamResponse.ofStatusUpdate(event));

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(requestCount.get()).isEqualTo(1));
    }

    @Test
    void scheduledRetryRemainsCountedAsPendingWork() throws Exception {
        AtomicInteger requestCount = new AtomicInteger(0);

        mockServer.createContext("/webhook", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
        });
        mockServer.start();

        dispatcher = new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 1, 5000, executor, true);

        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("http://localhost:" + port + "/webhook");
        store.putPushConfig("task-1", config);

        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        dispatcher.dispatch("task-1", StreamResponse.ofStatusUpdate(event));

        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(requestCount.get()).isEqualTo(1));
        await().atMost(2, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(pendingWork(dispatcher)).isEqualTo(1));
    }

    @Test
    void dispatchMultipleConfigsInParallel() throws Exception {
        CountDownLatch latch = new CountDownLatch(3);

        mockServer.createContext("/hook1", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            latch.countDown();
        });
        mockServer.createContext("/hook2", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            latch.countDown();
        });
        mockServer.createContext("/hook3", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            latch.countDown();
        });
        mockServer.start();

        dispatcher = new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 0, 1000, executor, true);

        for (int i = 1; i <= 3; i++) {
            TaskPushNotificationConfig config = new TaskPushNotificationConfig();
            config.setUrl("http://localhost:" + port + "/hook" + i);
            store.putPushConfig("task-1", config);
        }

        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        dispatcher.dispatch("task-1", StreamResponse.ofStatusUpdate(event));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void dispatchSkipsWhenNoConfigs() throws Exception {
        dispatcher = new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 0, 1000, executor, true);

        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        dispatcher.dispatch("task-1", StreamResponse.ofStatusUpdate(event));

        // No push configs registered, so no work should have been dispatched
        assertThat(pendingWork(dispatcher)).isEqualTo(0);
    }

    @Test
    void rejectsTightRetryLoopConfiguration() {
        assertThatThrownBy(() -> new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 1, 0, executor, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("initialBackoffMs");
    }

    @Test
    void dispatchUsesTokenAsBearerFallback() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedAuth = new AtomicReference<>();

        mockServer.createContext("/webhook", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            latch.countDown();
        });
        mockServer.start();

        dispatcher = new PushNotificationDispatcher(
                HttpClient.newHttpClient(), store, 0, 1000, executor, true);

        TaskPushNotificationConfig config = new TaskPushNotificationConfig();
        config.setUrl("http://localhost:" + port + "/webhook");
        config.setToken("opaque-token-xyz");
        store.putPushConfig("task-1", config);

        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        dispatcher.dispatch("task-1", StreamResponse.ofStatusUpdate(event));

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedAuth.get()).isEqualTo("Bearer opaque-token-xyz");
    }

    private static int pendingWork(PushNotificationDispatcher dispatcher) throws Exception {
        Field field = PushNotificationDispatcher.class.getDeclaredField("pendingWork");
        field.setAccessible(true);
        return ((AtomicInteger) field.get(dispatcher)).get();
    }
}
