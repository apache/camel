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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An {@link InputStream} backed by a {@link BlockingQueue} of SSE-encoded strings. Bridges asynchronous task store
 * events to Camel's synchronous Exchange body model.
 * <p>
 * When the HTTP component reads this stream, it blocks on the queue until the next SSE frame arrives. On poll timeout,
 * an SSE heartbeat comment ({@code :\n\n}) is emitted to keep the connection alive. The stream ends (returns -1) only
 * when the explicit {@link #EOF_MARKER} is received from a terminal task state.
 */
public class SseQueueInputStream extends InputStream {

    static final String EOF_MARKER = "\0EOF";
    private static final byte[] HEARTBEAT = ":\n\n".getBytes(StandardCharsets.UTF_8);

    private final BlockingQueue<String> queue;
    private final long heartbeatIntervalMs;
    private Runnable onClose;

    private byte[] currentChunk;
    private int pos;
    private volatile boolean finished;

    public SseQueueInputStream(BlockingQueue<String> queue, long heartbeatIntervalMs) {
        this.queue = queue;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public int read() throws IOException {
        if (finished) {
            return -1;
        }

        if (currentChunk != null && pos < currentChunk.length) {
            return currentChunk[pos++] & 0xFF;
        }

        if (!loadNextChunk()) {
            return -1;
        }
        return currentChunk[pos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (finished) {
            return -1;
        }
        if (b == null) {
            throw new NullPointerException();
        }
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) {
            return 0;
        }

        if (currentChunk == null || pos >= currentChunk.length) {
            if (!loadNextChunk()) {
                return -1;
            }
        }

        int remaining = currentChunk.length - pos;
        int toRead = Math.min(remaining, len);
        System.arraycopy(currentChunk, pos, b, off, toRead);
        pos += toRead;
        return toRead;
    }

    @Override
    public void close() throws IOException {
        finished = true;
        queue.clear();
        if (onClose != null) {
            onClose.run();
        }
    }

    /**
     * Loads the next chunk from the queue. Blocks until data arrives, emitting SSE heartbeat comments on timeout to
     * keep the connection alive. Returns false only when EOF is reached.
     */
    private boolean loadNextChunk() throws IOException {
        while (!finished) {
            try {
                String next = queue.poll(heartbeatIntervalMs, TimeUnit.MILLISECONDS);
                if (next == null) {
                    // Timeout — send SSE heartbeat to keep connection alive
                    currentChunk = HEARTBEAT;
                    pos = 0;
                    return true;
                }
                if (EOF_MARKER.equals(next)) {
                    finished = true;
                    return false;
                }
                currentChunk = next.getBytes(StandardCharsets.UTF_8);
                pos = 0;
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for SSE event", e);
            }
        }
        return false;
    }
}
