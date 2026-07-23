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
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.component.a2a.protocol.SseCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStreamEmitterTest {

    @Test
    void emitStatusCreatesStatusUpdateEvent() {
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");

        emitter.emitStatus(TaskState.WORKING, "Processing...");

        List<StreamResponse> events = emitter.getEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatusUpdate()).isNotNull();
        assertThat(events.get(0).getStatusUpdate().taskId()).isEqualTo("task-1");
        assertThat(events.get(0).getStatusUpdate().contextId()).isEqualTo("ctx-1");
        assertThat(events.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        assertThat(events.get(0).getStatusUpdate().isFinal()).isFalse();
        assertThat(events.get(0).getStatusUpdate().status().message()).isNotNull();
        assertThat(events.get(0).getStatusUpdate().status().message().role()).isEqualTo(Message.Role.ROLE_AGENT);
        assertThat(events.get(0).getStatusUpdate().status().message().parts()).hasSize(1);
        assertThat(((TextPart) events.get(0).getStatusUpdate().status().message().parts().get(0)).text())
                .isEqualTo("Processing...");
    }

    @Test
    void emitStatusWithNullMessageOmitsMessageField() {
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");

        emitter.emitStatus(TaskState.WORKING, null);

        assertThat(emitter.getEvents().get(0).getStatusUpdate().status().message()).isNull();
    }

    @Test
    void emitTerminalStatusSetsIsFinal() {
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");

        emitter.emitStatus(TaskState.COMPLETED, "Done");

        assertThat(emitter.getEvents().get(0).getStatusUpdate().isFinal()).isTrue();
    }

    @Test
    void emitArtifactCreatesArtifactUpdateEvent() {
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");
        Artifact artifact = Artifact.builder().name("result.txt").build();

        emitter.emitArtifact(artifact, false, true);

        List<StreamResponse> events = emitter.getEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getArtifactUpdate()).isNotNull();
        assertThat(events.get(0).getArtifactUpdate().artifact().name()).isEqualTo("result.txt");
        assertThat(events.get(0).getArtifactUpdate().append()).isFalse();
        assertThat(events.get(0).getArtifactUpdate().lastChunk()).isTrue();
    }

    @Test
    void emitMessageCreatesMessageEvent() {
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");
        Message message = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Hello!")))
                .build();

        emitter.emitMessage(message);

        List<StreamResponse> events = emitter.getEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getMessage()).isNotNull();
        assertThat(events.get(0).getMessage().role()).isEqualTo(Message.Role.ROLE_AGENT);
    }

    @Test
    void closedEmitterIgnoresNewEvents() {
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");
        emitter.emitStatus(TaskState.WORKING, null);
        emitter.close();

        assertThat(emitter.isClosed()).isTrue();

        emitter.emitStatus(TaskState.COMPLETED, "Done");
        assertThat(emitter.getEvents()).hasSize(1);
    }

    @Test
    void toSseResponseProducesValidSseFrames() {
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");
        emitter.emitStatus(TaskState.WORKING, null);
        emitter.emitStatus(TaskState.COMPLETED, "Done");

        String sse = emitter.toSseResponse();

        assertThat(sse).contains("data: ");
        assertThat(sse).contains("\n\n");

        List<StreamResponse> decoded = SseCodec.decodeAll(sse);
        assertThat(decoded).hasSize(2);
        assertThat(decoded.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        assertThat(decoded.get(1).getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
    }

    @Test
    void multipleEventTypesInterleavedCorrectly() {
        DefaultStreamEmitter emitter = new DefaultStreamEmitter("task-1", "ctx-1");

        emitter.emitStatus(TaskState.WORKING, null);

        Message msg = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("Thinking...")))
                .build();
        emitter.emitMessage(msg);

        Artifact artifact = Artifact.builder().name("output.json").build();
        emitter.emitArtifact(artifact, false, true);

        emitter.emitStatus(TaskState.COMPLETED, "All done");

        assertThat(emitter.getEvents()).hasSize(4);
        assertThat(emitter.getEvents().get(0).getStatusUpdate()).isNotNull();
        assertThat(emitter.getEvents().get(1).getMessage()).isNotNull();
        assertThat(emitter.getEvents().get(2).getArtifactUpdate()).isNotNull();
        assertThat(emitter.getEvents().get(3).getStatusUpdate()).isNotNull();
    }
}
