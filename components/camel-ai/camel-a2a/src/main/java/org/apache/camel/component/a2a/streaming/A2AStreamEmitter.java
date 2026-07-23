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

import java.util.List;

import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskArtifactUpdateEvent;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.model.TextPart;

/**
 * Interface for routes to emit SSE events during A2A task execution.
 * <p>
 * Implementations send Server-Sent Events to the client for real-time updates on task status, artifacts, and messages.
 */
public interface A2AStreamEmitter {

    /**
     * Emit a status update event.
     *
     * @param state   the current task state
     * @param message optional status message
     */
    void emitStatus(TaskState state, String message);

    /**
     * Emit an artifact event (text or binary data chunk).
     *
     * @param artifact  the artifact to emit
     * @param append    whether to append to an existing artifact
     * @param lastChunk whether this is the final chunk for the artifact
     */
    void emitArtifact(Artifact artifact, Boolean append, Boolean lastChunk);

    /**
     * Emit a message event (chat message or system notification).
     *
     * @param message the message to emit
     */
    void emitMessage(Message message);

    /**
     * Emit a task snapshot event.
     *
     * @param task the task snapshot to emit
     */
    default void emitTask(Task task) {
        // Custom emitters that do not support task snapshots can ignore them.
    }

    /**
     * Check if the emitter stream is closed (client disconnected).
     *
     * @return true if closed, false otherwise
     */
    boolean isClosed();

    static StreamResponse buildStatusResponse(String taskId, String contextId, TaskState state, String message) {
        TaskStatus status;
        if (message != null && !message.isEmpty()) {
            Message msg = Message.builder()
                    .role(Message.Role.ROLE_AGENT)
                    .parts(List.of(new TextPart(message)))
                    .build();
            status = new TaskStatus(state, msg);
        } else {
            status = new TaskStatus(state);
        }
        TaskStatusUpdateEvent event = TaskStatusUpdateEvent.builder()
                .taskId(taskId)
                .contextId(contextId)
                .status(status)
                .build();
        return StreamResponse.ofStatusUpdate(event);
    }

    static StreamResponse buildArtifactResponse(
            String taskId, String contextId,
            Artifact artifact, Boolean append, Boolean lastChunk) {
        TaskArtifactUpdateEvent event = TaskArtifactUpdateEvent.builder()
                .taskId(taskId)
                .contextId(contextId)
                .artifact(artifact)
                .append(append)
                .lastChunk(lastChunk)
                .build();
        return StreamResponse.ofArtifactUpdate(event);
    }
}
