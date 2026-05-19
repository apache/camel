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

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskArtifactUpdateEvent;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.state.A2ATaskStore;

/**
 * Static utility for emitting A2A progress updates from any exchange. Resolves the task store via the exchange's
 * {@code fromEndpoint} (primary) or by scanning the A2A component's endpoints (fallback).
 */
public final class A2AProgress {

    private A2AProgress() {
    }

    /**
     * Emit a status update with {@link TaskState#WORKING} state.
     *
     * @param exchange the current exchange
     * @param message  human-readable progress message
     */
    public static void emit(Exchange exchange, String message) {
        emit(exchange, TaskState.WORKING, message);
    }

    /**
     * Emit a status update with the given state.
     *
     * @param exchange the current exchange
     * @param state    the task state
     * @param message  human-readable progress message
     */
    public static void emit(Exchange exchange, TaskState state, String message) {
        String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
        A2ATaskStore store = findStore(exchange, taskId);
        if (store == null || taskId == null) {
            return;
        }

        TaskStatus status = buildStatus(state, message);
        store.updateStatusAndNotify(taskId, status);
    }

    /**
     * Emit an artifact.
     *
     * @param exchange  the current exchange
     * @param artifact  the artifact to emit
     * @param append    whether to append to an existing artifact
     * @param lastChunk whether this is the final chunk
     */
    public static void emitArtifact(Exchange exchange, Artifact artifact, Boolean append, Boolean lastChunk) {
        String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
        A2ATaskStore store = findStore(exchange, taskId);
        if (store == null || taskId == null) {
            return;
        }

        synchronized (store) {
            Task task = store.get(taskId);
            if (task != null) {
                List<Artifact> artifacts = task.artifacts() != null ? new ArrayList<>(task.artifacts()) : new ArrayList<>();
                artifacts.add(artifact);
                Task updated = Task.builder(task).artifacts(artifacts).build();
                store.put(taskId, updated);

                TaskArtifactUpdateEvent event = TaskArtifactUpdateEvent.builder()
                        .taskId(taskId).contextId(task.contextId())
                        .artifact(artifact).append(append).lastChunk(lastChunk).build();
                store.notifySubscribers(taskId, StreamResponse.ofArtifactUpdate(event));
            }
        }
    }

    /**
     * Emit a message.
     *
     * @param exchange the current exchange
     * @param message  the message to emit
     */
    public static void emitMessage(Exchange exchange, Message message) {
        String taskId = exchange.getMessage().getHeader(A2AConstants.TASK_ID, String.class);
        A2ATaskStore store = findStore(exchange, taskId);
        if (store == null || taskId == null) {
            return;
        }

        synchronized (store) {
            Task task = store.get(taskId);
            if (task != null) {
                List<Message> history = task.history() != null ? new ArrayList<>(task.history()) : new ArrayList<>();
                history.add(message);
                Task updated = Task.builder(task).history(history).build();
                store.put(taskId, updated);
                store.notifySubscribers(taskId, StreamResponse.ofMessage(message));
            }
        }
    }

    /**
     * Resolve the task store from the exchange. Primary path: from endpoint. Fallback: scan component endpoints.
     * Package-private for testability.
     */
    static A2ATaskStore findStore(Exchange exchange, String taskId) {
        // Primary: from endpoint — O(1)
        Endpoint from = exchange.getFromEndpoint();
        if (from instanceof A2AEndpoint a2aEndpoint) {
            A2ATaskStore store = a2aEndpoint.getTaskStore();
            if (store != null) {
                return store;
            }
        }
        // Fallback: scan component endpoints
        try {
            if (taskId != null) {
                for (Endpoint ep : exchange.getContext().getEndpoints()) {
                    if (ep instanceof A2AEndpoint a2aEp) {
                        A2ATaskStore store = a2aEp.getTaskStore();
                        if (store != null && store.get(taskId) != null) {
                            return store;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Component not found
        }
        return null;
    }

    private static TaskStatus buildStatus(TaskState state, String message) {
        if (message != null && !message.isEmpty()) {
            Message msg = Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .parts(List.of(new TextPart(message)))
                    .build();
            return new TaskStatus(state, msg);
        }
        return new TaskStatus(state);
    }
}
