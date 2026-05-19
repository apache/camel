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
package org.apache.camel.component.a2a.streaming;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.protocol.SseCodec;

/**
 * Default implementation of {@link A2AStreamEmitter} that collects SSE events during route processing and produces an
 * SSE-formatted response body. Events are buffered in memory and can be retrieved as a single SSE response string via
 * {@link #toSseResponse()}.
 * <p>
 * This implementation supports both synchronous (collect-then-return) and future real-time streaming patterns. The
 * collected {@link StreamResponse} events are also available via {@link #getEvents()} for programmatic access.
 */
public class DefaultStreamEmitter implements A2AStreamEmitter {

    private final String taskId;
    private final String contextId;
    private final List<StreamResponse> events = new ArrayList<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DefaultStreamEmitter(String taskId, String contextId) {
        this.taskId = taskId;
        this.contextId = contextId;
    }

    @Override
    public void emitStatus(TaskState state, String message) {
        if (closed.get()) {
            return;
        }
        events.add(A2AStreamEmitter.buildStatusResponse(taskId, contextId, state, message));
    }

    @Override
    public void emitArtifact(Artifact artifact, Boolean append, Boolean lastChunk) {
        if (closed.get()) {
            return;
        }
        events.add(A2AStreamEmitter.buildArtifactResponse(taskId, contextId, artifact, append, lastChunk));
    }

    @Override
    public void emitMessage(Message message) {
        if (closed.get()) {
            return;
        }
        events.add(StreamResponse.ofMessage(message));
    }

    @Override
    public void emitTask(Task task) {
        if (closed.get()) {
            return;
        }
        events.add(StreamResponse.ofTask(task));
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    public void close() {
        closed.set(true);
    }

    public List<StreamResponse> getEvents() {
        return events;
    }

    public String toSseResponse() {
        StringBuilder sb = new StringBuilder();
        for (StreamResponse event : events) {
            sb.append(SseCodec.encode(event));
        }
        return sb.toString();
    }
}
