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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.protocol.SseCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link A2AStreamEmitter} that encodes each event as an SSE frame via {@link SseCodec} and offers it to a
 * {@link BlockingQueue}. Paired with {@link SseQueueInputStream} to bridge asynchronous task store notifications to
 * Camel's synchronous Exchange model.
 * <p>
 * When a terminal status event is emitted, the emitter automatically closes and sends an EOF marker to signal the end
 * of the stream.
 */
public class QueueStreamEmitter implements A2AStreamEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(QueueStreamEmitter.class);

    private final String taskId;
    private final String contextId;
    private final BlockingQueue<String> queue;
    private final Function<StreamResponse, String> encoder;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public QueueStreamEmitter(String taskId, String contextId, BlockingQueue<String> queue) {
        this(taskId, contextId, queue, SseCodec::encode);
    }

    public QueueStreamEmitter(String taskId, String contextId, BlockingQueue<String> queue,
                              Function<StreamResponse, String> encoder) {
        this.taskId = taskId;
        this.contextId = contextId;
        this.queue = queue;
        this.encoder = encoder;
    }

    @Override
    public void emitStatus(TaskState state, String message) {
        StreamResponse response = A2AStreamEmitter.buildStatusResponse(taskId, contextId, state, message);
        emit(response);
    }

    @Override
    public void emitArtifact(Artifact artifact, Boolean append, Boolean lastChunk) {
        StreamResponse response = A2AStreamEmitter.buildArtifactResponse(taskId, contextId, artifact, append, lastChunk);
        emit(response);
    }

    @Override
    public void emitMessage(Message message) {
        emit(StreamResponse.ofMessage(message));
    }

    @Override
    public void emitTask(Task task) {
        emit(StreamResponse.ofTask(task));
    }

    public void emit(StreamResponse response) {
        if (response == null || closed.get()) {
            return;
        }
        String frame = encoder.apply(response);
        if (isTerminalStatus(response)) {
            closeWithTerminalFrame(frame);
        } else {
            offerFrame(frame, false);
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            offerFrame(SseQueueInputStream.EOF_MARKER, true);
        }
    }

    private void closeWithTerminalFrame(String frame) {
        if (closed.compareAndSet(false, true)) {
            reserveRequiredSlots(2);
            offerFrame(frame, true);
            offerFrame(SseQueueInputStream.EOF_MARKER, true);
        }
    }

    private static boolean isTerminalStatus(StreamResponse response) {
        if (response.getStatusUpdate() == null) {
            return false;
        }
        TaskStatus status = response.getStatusUpdate().status();
        TaskState state = status != null ? status.state() : null;
        return state != null && state.isTerminal();
    }

    private void reserveRequiredSlots(int slots) {
        while (queue.remainingCapacity() < slots && queue.poll() != null) {
            LOG.warn("Dropped an older SSE event for task {} to enqueue required terminal frames", taskId);
        }
    }

    private void offerFrame(String frame, boolean required) {
        if (queue.offer(frame)) {
            return;
        }
        if (required) {
            queue.poll();
            if (queue.offer(frame)) {
                LOG.warn("Dropped an older SSE event for task {} to enqueue required terminal frame", taskId);
                return;
            }
        }
        LOG.warn("SSE event dropped for task {} — queue full (capacity exhausted)", taskId);
    }
}
