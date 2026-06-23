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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.model.TaskStatus;
import org.apache.camel.component.a2a.model.TaskStatusUpdateEvent;
import org.apache.camel.component.a2a.protocol.RestProtocol;
import org.apache.camel.component.a2a.protocol.SseCodec;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseEventIteratorTest {

    private final RestProtocol restProtocol = new RestProtocol();

    @Test
    void iteratesOverMultipleEvents() throws IOException {
        String sse = buildSseFrame(TaskState.WORKING, "task-1")
                     + buildSseFrame(TaskState.COMPLETED, "task-1");

        try (SseEventIterator it = new SseEventIterator(toInputStream(sse), restProtocol)) {
            List<StreamResponse> events = drain(it);
            assertThat(events).hasSize(2);
            assertThat(events.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
            assertThat(events.get(1).getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
        }
    }

    @Test
    void handlesEmptyStream() throws IOException {
        try (SseEventIterator it = new SseEventIterator(toInputStream(""), restProtocol)) {
            assertThat(it.hasNext()).isFalse();
        }
    }

    @Test
    void skipsSseComments() throws IOException {
        String sse = ": this is a comment\n"
                     + buildSseFrame(TaskState.WORKING, "task-1");

        try (SseEventIterator it = new SseEventIterator(toInputStream(sse), restProtocol)) {
            List<StreamResponse> events = drain(it);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        }
    }

    @Test
    void handlesMultilineDataEvent() throws IOException {
        String sse = "data: {\"statusUpdate\":{\"taskId\":\"task-ml\",\n"
                     + "data: \"status\":{\"state\":\"TASK_STATE_WORKING\"}}}\n\n";

        try (SseEventIterator it = new SseEventIterator(toInputStream(sse), restProtocol)) {
            List<StreamResponse> events = drain(it);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getStatusUpdate().taskId()).isEqualTo("task-ml");
            assertThat(events.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
        }
    }

    @Test
    void handlesFinalEventWithoutTrailingNewline() throws IOException {
        TaskStatusUpdateEvent update = TaskStatusUpdateEvent.builder()
                .taskId("task-1")
                .status(new TaskStatus(TaskState.COMPLETED))
                .build();
        String frame = SseCodec.encode(StreamResponse.ofStatusUpdate(update));
        String withoutTrailing = frame.stripTrailing();

        try (SseEventIterator it = new SseEventIterator(toInputStream(withoutTrailing), restProtocol)) {
            List<StreamResponse> events = drain(it);
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getStatusUpdate().status().state()).isEqualTo(TaskState.COMPLETED);
        }
    }

    @Test
    void throwsNoSuchElementWhenExhausted() throws IOException {
        try (SseEventIterator it = new SseEventIterator(toInputStream(""), restProtocol)) {
            assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
        }
    }

    @Test
    void closeReleasesUnderlyingStream() throws IOException {
        CloseTrackingInputStream tracking = new CloseTrackingInputStream(toInputStream(""));
        SseEventIterator it = new SseEventIterator(tracking, restProtocol);

        assertThat(tracking.closed).isFalse();
        it.close();
        assertThat(tracking.closed).isTrue();
    }

    @Test
    void hasNextIsIdempotent() throws IOException {
        String sse = buildSseFrame(TaskState.WORKING, "task-1");

        try (SseEventIterator it = new SseEventIterator(toInputStream(sse), restProtocol)) {
            assertThat(it.hasNext()).isTrue();
            assertThat(it.hasNext()).isTrue();
            assertThat(it.next().getStatusUpdate().status().state()).isEqualTo(TaskState.WORKING);
            assertThat(it.hasNext()).isFalse();
            assertThat(it.hasNext()).isFalse();
        }
    }

    @Test
    void readTimeoutFiresWhenPeerIsSilent() throws IOException {
        BlockingInputStream inputStream = new BlockingInputStream();

        try (SseEventIterator it = new SseEventIterator(inputStream, restProtocol, 25)) {
            assertThatThrownBy(it::hasNext)
                    .isInstanceOf(RuntimeCamelException.class)
                    .hasMessageContaining("SSE stream read timeout");
        }
        assertThat(inputStream.closed).isTrue();
    }

    @Test
    void rejectsOversizedSseEvent() throws IOException {
        String sse = buildSseFrame(TaskState.WORKING, "task-oversized");

        try (SseEventIterator it = new SseEventIterator(toInputStream(sse), restProtocol, 0, 16)) {
            assertThatThrownBy(it::hasNext)
                    .isInstanceOf(RuntimeCamelException.class)
                    .hasMessageContaining("SSE event exceeds maximum size");
        }
    }

    private static String buildSseFrame(TaskState state, String taskId) {
        TaskStatusUpdateEvent update = TaskStatusUpdateEvent.builder()
                .taskId(taskId)
                .status(new TaskStatus(state))
                .build();
        return SseCodec.encode(StreamResponse.ofStatusUpdate(update));
    }

    private static InputStream toInputStream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<StreamResponse> drain(SseEventIterator it) {
        List<StreamResponse> result = new ArrayList<>();
        while (it.hasNext()) {
            result.add(it.next());
        }
        return result;
    }

    private static class CloseTrackingInputStream extends InputStream {
        private final InputStream delegate;
        boolean closed;

        CloseTrackingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }

    private static class BlockingInputStream extends InputStream {
        private final CountDownLatch closedLatch = new CountDownLatch(1);
        private volatile boolean closed;

        @Override
        public int read() throws IOException {
            try {
                closedLatch.await();
                return -1;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while blocked", e);
            }
        }

        @Override
        public void close() {
            closed = true;
            closedLatch.countDown();
        }
    }
}
