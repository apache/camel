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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link A2AProgress} safety behavior. The full store integration (findStore via endpoint) is covered by
 * {@link A2AConsumerTest} and {@link org.apache.camel.component.a2a.state.InMemoryTaskStoreTest}.
 */
class A2AProgressTest {

    private CamelContext context;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void emitWithoutTaskIdDoesNotThrow() {
        Exchange exchange = new DefaultExchange(context);
        A2AProgress.emit(exchange, "safe message");
        // emit is a safe no-op when there is no task context
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage()).isNotNull();
    }

    @Test
    void emitWithoutStoreDoesNotThrow() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(A2AConstants.TASK_ID, "t1");
        A2AProgress.emit(exchange, "safe message");
        // emit is a safe no-op when no task store is available
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage().getHeader(A2AConstants.TASK_ID)).isEqualTo("t1");
    }

    @Test
    void emitWithExplicitStateDoesNotThrow() {
        Exchange exchange = new DefaultExchange(context);
        A2AProgress.emit(exchange, TaskState.INPUT_REQUIRED, "need info");
        // emit with explicit state is a safe no-op when there is no task context
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage()).isNotNull();
    }

    @Test
    void emitArtifactWithoutStoreDoesNotThrow() {
        Exchange exchange = new DefaultExchange(context);
        A2AProgress.emitArtifact(exchange, Artifact.builder().name("test").build(), false, true);
        // emitArtifact is a safe no-op when there is no task context
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage()).isNotNull();
    }

    @Test
    void emitMessageWithoutStoreDoesNotThrow() {
        Exchange exchange = new DefaultExchange(context);
        Message msg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("hello")))
                .build();
        A2AProgress.emitMessage(exchange, msg);
        // emitMessage is a safe no-op when there is no task context
        assertThat(exchange.getException()).isNull();
        assertThat(exchange.getMessage()).isNotNull();
    }

    @Test
    void findStoreReturnsNullWhenNoEndpoint() {
        Exchange exchange = new DefaultExchange(context);
        assertThat(A2AProgress.findStore(exchange, "t1")).isNull();
    }

    @Test
    void hasTaskContextReturnsFalseWithoutTaskId() {
        Exchange exchange = new DefaultExchange(context);
        assertThat(A2AProgress.hasTaskContext(exchange)).isFalse();
    }

    @Test
    void hasTaskContextReturnsFalseWithoutTaskStore() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getMessage().setHeader(A2AConstants.TASK_ID, "t1");
        assertThat(A2AProgress.hasTaskContext(exchange)).isFalse();
    }

    // ---- Positive-path tests ----

    private A2AEndpoint createEndpointWithTask(String taskId) throws Exception {
        A2AConfiguration config = new A2AConfiguration();
        config.setName("Progress Agent");
        config.setVersion("1.0.0");

        A2AComponent component = new A2AComponent();
        component.setCamelContext(context);

        A2AEndpoint endpoint = new A2AEndpoint("a2a:progress-agent", component, config);
        endpoint.setAgentCardSource("progress-agent");
        endpoint.setCamelContext(context);
        endpoint.doStart();

        Task task = Task.builder()
                .id(taskId)
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.WORKING))
                .build();
        endpoint.getTaskStore().put(taskId, task);

        return endpoint;
    }

    private Exchange createExchangeWithEndpoint(A2AEndpoint endpoint, String taskId) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getExchangeExtension().setFromEndpoint(endpoint);
        exchange.getMessage().setHeader(A2AConstants.TASK_ID, taskId);
        return exchange;
    }

    @Test
    void emitProducesStatusUpdateEvent() throws Exception {
        String taskId = "task-emit";
        A2AEndpoint endpoint = createEndpointWithTask(taskId);

        List<StreamResponse> received = new CopyOnWriteArrayList<>();
        endpoint.getTaskStore().addSubscriber(taskId, (id, event) -> received.add(event));

        Exchange exchange = createExchangeWithEndpoint(endpoint, taskId);
        A2AProgress.emit(exchange, "working on it");

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getStatusUpdate()).isNotNull();
        assertThat(received.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
    }

    @Test
    void hasTaskContextReturnsTrueWhenTaskStoreHasTask() throws Exception {
        String taskId = "task-context";
        A2AEndpoint endpoint = createEndpointWithTask(taskId);

        Exchange exchange = createExchangeWithEndpoint(endpoint, taskId);

        assertThat(A2AProgress.hasTaskContext(exchange)).isTrue();
    }

    @Test
    void emitWithExplicitStateProducesCorrectEvent() throws Exception {
        String taskId = "task-emit-state";
        A2AEndpoint endpoint = createEndpointWithTask(taskId);

        List<StreamResponse> received = new CopyOnWriteArrayList<>();
        endpoint.getTaskStore().addSubscriber(taskId, (id, event) -> received.add(event));

        Exchange exchange = createExchangeWithEndpoint(endpoint, taskId);
        A2AProgress.emit(exchange, TaskState.INPUT_REQUIRED, "need info");

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.INPUT_REQUIRED);
    }

    @Test
    void emitArtifactProducesArtifactUpdateEvent() throws Exception {
        String taskId = "task-artifact";
        A2AEndpoint endpoint = createEndpointWithTask(taskId);

        List<StreamResponse> received = new CopyOnWriteArrayList<>();
        endpoint.getTaskStore().addSubscriber(taskId, (id, event) -> received.add(event));

        Exchange exchange = createExchangeWithEndpoint(endpoint, taskId);
        Artifact artifact = Artifact.builder().name("output.txt").build();
        A2AProgress.emitArtifact(exchange, artifact, false, true);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getArtifactUpdate()).isNotNull();
        assertThat(received.get(0).getArtifactUpdate().artifact().name()).isEqualTo("output.txt");
        assertThat(received.get(0).getArtifactUpdate().lastChunk()).isTrue();
    }

    @Test
    void emitMessageProducesMessageEvent() throws Exception {
        String taskId = "task-message";
        A2AEndpoint endpoint = createEndpointWithTask(taskId);

        List<StreamResponse> received = new CopyOnWriteArrayList<>();
        endpoint.getTaskStore().addSubscriber(taskId, (id, event) -> received.add(event));

        Exchange exchange = createExchangeWithEndpoint(endpoint, taskId);
        Message msg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("hello from agent")))
                .build();
        A2AProgress.emitMessage(exchange, msg);

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getMessage()).isNotNull();
        assertThat(received.get(0).getMessage().role()).isEqualTo(Message.Role.ROLE_AGENT);
    }
}
