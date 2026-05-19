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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseQueueInputStreamTest {

    @Test
    void readsPreQueuedData() throws IOException {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        queue.offer("data: hello\n\n");
        queue.offer(SseQueueInputStream.EOF_MARKER);

        SseQueueInputStream stream = new SseQueueInputStream(queue, 1000);
        byte[] buf = new byte[256];
        int read = stream.read(buf, 0, buf.length);

        assertThat(read).isGreaterThan(0);
        String result = new String(buf, 0, read, StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("data: hello\n\n");

        assertThat(stream.read()).isEqualTo(-1);
        stream.close();
    }

    @Test
    void eofMarkerEndsStream() throws IOException {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        queue.offer(SseQueueInputStream.EOF_MARKER);

        SseQueueInputStream stream = new SseQueueInputStream(queue, 1000);
        assertThat(stream.read()).isEqualTo(-1);
        stream.close();
    }

    @Test
    void heartbeatOnTimeout() throws IOException {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        SseQueueInputStream stream = new SseQueueInputStream(queue, 50);

        byte[] buf = new byte[256];
        int read = stream.read(buf, 0, buf.length);

        String result = new String(buf, 0, read, StandardCharsets.UTF_8);
        assertThat(result).isEqualTo(":\n\n");

        queue.offer(SseQueueInputStream.EOF_MARKER);
        assertThat(stream.read()).isEqualTo(-1);
        stream.close();
    }

    @Test
    void bulkReadHandsBytesBoundaries() throws IOException {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        queue.offer("data: {\"large\":\"payload\"}\n\n");
        queue.offer(SseQueueInputStream.EOF_MARKER);

        SseQueueInputStream stream = new SseQueueInputStream(queue, 1000);

        byte[] smallBuf = new byte[10];
        StringBuilder sb = new StringBuilder();
        int read;
        while ((read = stream.read(smallBuf, 0, smallBuf.length)) != -1) {
            sb.append(new String(smallBuf, 0, read, StandardCharsets.UTF_8));
        }

        assertThat(sb.toString()).isEqualTo("data: {\"large\":\"payload\"}\n\n");
        stream.close();
    }

    @Test
    void multipleEventsReadSequentially() throws IOException {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        queue.offer("data: event1\n\n");
        queue.offer("data: event2\n\n");
        queue.offer(SseQueueInputStream.EOF_MARKER);

        SseQueueInputStream stream = new SseQueueInputStream(queue, 1000);

        byte[] buf = new byte[256];
        StringBuilder sb = new StringBuilder();
        int read;
        while ((read = stream.read(buf, 0, buf.length)) != -1) {
            sb.append(new String(buf, 0, read, StandardCharsets.UTF_8));
        }

        assertThat(sb.toString()).isEqualTo("data: event1\n\ndata: event2\n\n");
        stream.close();
    }

    @Test
    void closeCallsOnCloseCallback() throws IOException {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        SseQueueInputStream stream = new SseQueueInputStream(queue, 1000);

        AtomicBoolean called = new AtomicBoolean(false);
        stream.setOnClose(() -> called.set(true));

        stream.close();

        assertThat(called.get()).isTrue();
        assertThat(stream.read()).isEqualTo(-1);
    }

    @Test
    void singleByteReadWorks() throws IOException {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        queue.offer("AB");
        queue.offer(SseQueueInputStream.EOF_MARKER);

        SseQueueInputStream stream = new SseQueueInputStream(queue, 1000);

        assertThat(stream.read()).isEqualTo('A');
        assertThat(stream.read()).isEqualTo('B');
        assertThat(stream.read()).isEqualTo(-1);
        stream.close();
    }
}
