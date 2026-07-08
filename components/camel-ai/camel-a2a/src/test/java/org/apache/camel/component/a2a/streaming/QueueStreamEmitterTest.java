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
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.component.a2a.model.Artifact;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.protocol.SseCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueueStreamEmitterTest {

    @Test
    void emitStatusEncodesAsSseAndQueues() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);

        emitter.emitStatus(TaskState.WORKING, null);

        assertThat(queue).hasSize(1);
        String frame = queue.poll();
        assertThat(frame).startsWith("data: ").endsWith("\n\n");

        StreamResponse decoded = SseCodec.decode(frame);
        assertThat(decoded.getStatusUpdate()).isNotNull();
        assertThat(decoded.getStatusUpdate().taskId()).isEqualTo("task-1");
        assertThat(decoded.getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        assertThat(decoded.getStatusUpdate().isFinal()).isFalse();
    }

    @Test
    void emitStatusWithMessagePreservesText() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);

        emitter.emitStatus(TaskState.WORKING, "Analyzing data...");

        String frame = queue.poll();
        StreamResponse decoded = SseCodec.decode(frame);
        assertThat(decoded.getStatusUpdate().status().message()).isNotNull();
        assertThat(decoded.getStatusUpdate().status().message().role()).isEqualTo(Message.Role.ROLE_AGENT);
        assertThat(decoded.getStatusUpdate().status().message().parts()).hasSize(1);
        assertThat(((TextPart) decoded.getStatusUpdate().status().message().parts().get(0)).text())
                .isEqualTo("Analyzing data...");
    }

    @Test
    void emitStatusWithNullMessageOmitsMessageField() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);

        emitter.emitStatus(TaskState.WORKING, null);

        StreamResponse decoded = SseCodec.decode(queue.poll());
        assertThat(decoded.getStatusUpdate().status().message()).isNull();
    }

    @Test
    void terminalStatusClosesEmitterAndSendsEof() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);

        emitter.emitStatus(TaskState.COMPLETED, null);

        assertThat(queue).hasSize(2);
        String eventFrame = queue.poll();
        assertThat(eventFrame).startsWith("data: ");

        StreamResponse decoded = SseCodec.decode(eventFrame);
        assertThat(decoded.getStatusUpdate().isFinal()).isTrue();

        String eof = queue.poll();
        assertThat(eof).isEqualTo(SseQueueInputStream.EOF_MARKER);
        assertThat(emitter.isClosed()).isTrue();
    }

    @Test
    void terminalStatusAndEofReplaceOlderFramesWhenQueueIsFull() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(2);
        queue.offer("data: old-1\n\n");
        queue.offer("data: old-2\n\n");
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);

        emitter.emitStatus(TaskState.COMPLETED, null);

        assertThat(queue).hasSize(2);
        String eventFrame = queue.poll();
        StreamResponse decoded = SseCodec.decode(eventFrame);
        assertThat(decoded.getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
        assertThat(queue.poll()).isEqualTo(SseQueueInputStream.EOF_MARKER);
    }

    @Test
    void emitArtifactEncodesCorrectly() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);

        Artifact artifact = Artifact.builder().name("result.json").build();
        emitter.emitArtifact(artifact, true, false);

        assertThat(queue).hasSize(1);
        StreamResponse decoded = SseCodec.decode(queue.poll());
        assertThat(decoded.getArtifactUpdate()).isNotNull();
        assertThat(decoded.getArtifactUpdate().artifact().name()).isEqualTo("result.json");
        assertThat(decoded.getArtifactUpdate().append()).isTrue();
        assertThat(decoded.getArtifactUpdate().lastChunk()).isFalse();
    }

    @Test
    void emitMessageEncodesCorrectly() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);

        Message message = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Processing...")))
                .build();
        emitter.emitMessage(message);

        assertThat(queue).hasSize(1);
        StreamResponse decoded = SseCodec.decode(queue.poll());
        assertThat(decoded.getMessage()).isNotNull();
        assertThat(decoded.getMessage().role()).isEqualTo(Message.Role.ROLE_AGENT);
    }

    @Test
    void closedEmitterDropsEvents() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);
        emitter.close();

        emitter.emitStatus(TaskState.WORKING, null);
        emitter.emitArtifact(Artifact.builder().build(), false, true);
        emitter.emitMessage(Message.builder().build());

        // Only the EOF marker from close()
        assertThat(queue).hasSize(1);
        assertThat(queue.poll()).isEqualTo(SseQueueInputStream.EOF_MARKER);
    }

    @Test
    void doubleCloseOnlySendsOneEof() {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        QueueStreamEmitter emitter = new QueueStreamEmitter("task-1", "ctx-1", queue);

        emitter.close();
        emitter.close();

        assertThat(queue).hasSize(1);
    }
}
