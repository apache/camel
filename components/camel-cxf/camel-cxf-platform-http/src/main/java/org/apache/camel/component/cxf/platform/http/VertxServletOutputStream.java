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
package org.apache.camel.component.cxf.platform.http;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

import io.vertx.core.Context;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;

/**
 * A blocking stream of the body of a Vert.x response, written on a worker thread. The bytes are kept until the buffer
 * is full; a body that fits in the buffer is sent with its length, a larger one is sent in chunks (unless the length is
 * set), waiting for the connection to drain when its write queue is full.
 */
final class VertxServletOutputStream extends ServletOutputStream {

    static final int DEFAULT_BUFFER_SIZE = 8192;

    private final Context context;
    private final HttpServerResponse response;
    private Buffer buffer;
    private int bufferSize = DEFAULT_BUFFER_SIZE;
    private boolean committed;
    private boolean closed;

    VertxServletOutputStream(Context context, HttpServerResponse response) {
        this.context = context;
        this.response = response;
        this.buffer = Buffer.buffer(bufferSize);
    }

    void setBufferSize(int size) {
        if (buffer.length() > 0 || committed) {
            throw new IllegalStateException("The response body has been written already");
        }
        bufferSize = Math.max(size, 1);
    }

    int getBufferSize() {
        return bufferSize;
    }

    boolean isCommitted() {
        return committed || response.headWritten();
    }

    void resetBuffer() {
        if (isCommitted()) {
            throw new IllegalStateException("The response has been committed");
        }
        buffer = Buffer.buffer(bufferSize);
    }

    @Override
    public void write(int b) throws IOException {
        ensureOpen();
        buffer.appendByte((byte) b);
        if (buffer.length() >= bufferSize) {
            writeBuffer();
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        ensureOpen();
        while (len > 0) {
            int n = Math.min(len, bufferSize - buffer.length());
            buffer.appendBytes(b, off, n);
            off += n;
            len -= n;
            if (buffer.length() >= bufferSize) {
                writeBuffer();
            }
        }
    }

    @Override
    public void flush() throws IOException {
        ensureOpen();
        if (buffer.length() > 0) {
            writeBuffer();
        }
    }

    /**
     * Commits the response (status and headers) and sends what is buffered.
     */
    private void writeBuffer() throws IOException {
        if (!committed) {
            committed = true;
            if (!response.headers().contains(HttpHeaders.CONTENT_LENGTH)) {
                response.setChunked(true);
            }
        }
        Buffer chunk = buffer;
        buffer = Buffer.buffer(bufferSize);
        if (response.closed()) {
            throw new IOException("The connection is closed");
        }
        response.write(chunk);
        waitForDrain();
    }

    private void waitForDrain() throws IOException {
        if (!response.writeQueueFull()) {
            return;
        }
        CompletableFuture<Void> drained = new CompletableFuture<>();
        context.runOnContext(v -> {
            if (!response.writeQueueFull() || response.closed()) {
                drained.complete(null);
            } else {
                response.drainHandler(d -> drained.complete(null));
            }
        });
        try {
            while (true) {
                try {
                    drained.get(1, TimeUnit.SECONDS);
                    break;
                } catch (TimeoutException e) {
                    if (response.closed()) {
                        throw new IOException("The connection is closed");
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted writing the response body");
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
        if (response.closed()) {
            throw new IOException("The connection is closed");
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (response.ended() || response.closed()) {
            return;
        }
        if (!committed) {
            // the whole body is in the buffer: send it with its length
            if (!response.headers().contains(HttpHeaders.CONTENT_LENGTH) && !response.isChunked()) {
                response.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()));
            }
            committed = true;
            response.end(buffer);
        } else {
            response.end(buffer);
        }
    }

    boolean isClosed() {
        return closed;
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("The response body is closed");
        }
    }

    @Override
    public boolean isReady() {
        return !response.writeQueueFull();
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        throw new UnsupportedOperationException("Asynchronous writes are not supported");
    }
}
