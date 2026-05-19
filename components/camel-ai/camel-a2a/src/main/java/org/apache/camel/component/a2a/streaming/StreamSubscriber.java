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

import org.apache.camel.component.a2a.model.Part;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.TaskArtifactUpdateEvent;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.state.A2ATaskSubscriber;

/**
 * An {@link A2ATaskSubscriber} that wraps an {@link A2AStreamEmitter} and translates {@link StreamResponse} events into
 * SSE writes. Moves the event dispatch logic (status/artifact/message branching) out of the store and into this
 * subscriber.
 */
public class StreamSubscriber implements A2ATaskSubscriber {

    private final A2AStreamEmitter emitter;

    public StreamSubscriber(A2AStreamEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onEvent(String taskId, StreamResponse event) {
        if (emitter.isClosed()) {
            return;
        }
        if (event.getTask() != null) {
            emitter.emitTask(event.getTask());
        } else if (event.getStatusUpdate() != null) {
            TaskStatus status = event.getStatusUpdate().status();
            TaskState state = status != null ? status.state() : null;
            String msgText = extractStatusMessageText(status);
            emitter.emitStatus(state, msgText);
        } else if (event.getArtifactUpdate() != null) {
            TaskArtifactUpdateEvent artifact = event.getArtifactUpdate();
            emitter.emitArtifact(artifact.artifact(), artifact.append(), artifact.lastChunk());
        } else if (event.getMessage() != null) {
            emitter.emitMessage(event.getMessage());
        }
    }

    /**
     * Extract the first text part from a status message, if present.
     */
    private static String extractStatusMessageText(TaskStatus status) {
        if (status == null || status.message() == null || status.message().parts() == null) {
            return null;
        }
        for (Part<?> part : status.message().parts()) {
            if (part instanceof TextPart textPart) {
                return textPart.text();
            }
        }
        return null;
    }

    public A2AStreamEmitter getEmitter() {
        return emitter;
    }
}
