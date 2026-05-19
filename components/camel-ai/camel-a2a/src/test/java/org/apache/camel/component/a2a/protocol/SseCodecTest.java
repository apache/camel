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
package org.apache.camel.component.a2a.protocol;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseCodecTest {

    @Test
    void encodesStreamResponseToSseFrame() {
        TaskStatus status = new TaskStatus(
                TaskState.WORKING, null,
                OffsetDateTime.of(2026, 5, 20, 10, 0, 0, 0, ZoneOffset.UTC));
        Task task = Task.builder()
                .id("task-123")
                .status(status)
                .build();

        StreamResponse response = StreamResponse.ofTask(task);
        String frame = SseCodec.encode(response);

        assertThat(frame).startsWith("data: ");
        assertThat(frame).endsWith("\n\n");

        StreamResponse decoded = SseCodec.decode(frame);
        assertThat(decoded.getTask()).isNotNull();
        assertThat(decoded.getTask().id()).isEqualTo("task-123");
        assertThat(decoded.getTask().status().state()).isEqualTo(TaskState.WORKING);
    }

    @Test
    void decodesStatusUpdateFromSseFrame() {
        TaskStatus status = new TaskStatus(
                TaskState.COMPLETED, null,
                OffsetDateTime.of(2026, 5, 20, 10, 5, 0, 0, ZoneOffset.UTC));
        TaskStatusUpdateEvent statusUpdate = TaskStatusUpdateEvent.builder()
                .taskId("task-456")
                .status(status)
                .build();

        StreamResponse response = StreamResponse.ofStatusUpdate(statusUpdate);
        String frame = SseCodec.encode(response);

        StreamResponse decoded = SseCodec.decode(frame);

        assertThat(decoded.getStatusUpdate()).isNotNull();
        assertThat(decoded.getStatusUpdate().taskId()).isEqualTo("task-456");
        assertThat(decoded.getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
    }

    @Test
    void handlesMultipleFrames() {
        TaskStatus status1 = new TaskStatus(
                TaskState.WORKING, null,
                OffsetDateTime.of(2026, 5, 20, 10, 0, 0, 0, ZoneOffset.UTC));
        TaskStatusUpdateEvent update1 = TaskStatusUpdateEvent.builder()
                .taskId("task-789")
                .status(status1)
                .build();

        TaskStatus status2 = new TaskStatus(
                TaskState.COMPLETED, null,
                OffsetDateTime.of(2026, 5, 20, 10, 0, 10, 0, ZoneOffset.UTC));
        TaskStatusUpdateEvent update2 = TaskStatusUpdateEvent.builder()
                .taskId("task-789")
                .status(status2)
                .build();

        String frame1 = SseCodec.encode(StreamResponse.ofStatusUpdate(update1));
        String frame2 = SseCodec.encode(StreamResponse.ofStatusUpdate(update2));
        String concatenated = frame1 + frame2;

        List<StreamResponse> responses = SseCodec.decodeAll(concatenated);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        assertThat(responses.get(1).getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
    }

    @Test
    void handlesMultilineDataFrame() {
        String frame = "data: {\"statusUpdate\":{\"taskId\":\"task-ml\",\n"
                       + "data: \"status\":{\"state\":\"TASK_STATE_WORKING\"}}}\n\n";

        StreamResponse decoded = SseCodec.decode(frame);

        assertThat(decoded.getStatusUpdate()).isNotNull();
        assertThat(decoded.getStatusUpdate().taskId()).isEqualTo("task-ml");
        assertThat(decoded.getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
    }
}
