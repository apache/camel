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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.protocol.A2AProtocol;
import org.apache.camel.component.a2a.protocol.JsonRpcProtocol;
import org.apache.camel.component.a2a.protocol.SseCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lazy iterator over an SSE (Server-Sent Events) stream that parses events on demand. Each call to {@link #next()}
 * blocks until the next SSE event arrives from the remote agent, then parses and returns it as a
 * {@link StreamResponse}.
 * <p>
 * Implements {@link Closeable} so Camel's Split EIP and {@code IOHelper.closeIterator()} can release the underlying
 * HTTP connection when iteration is complete or abandoned.
 * <p>
 * Supports both REST (plain SSE) and JSON-RPC (wrapped SSE) protocols.
 */
public class SseEventIterator implements Iterator<StreamResponse>, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(SseEventIterator.class);

    private final BufferedReader reader;
    private final A2AProtocol protocol;
    private final boolean isJsonRpc;
    private final long readTimeoutMs;
    private final long maxEventBytes;
    private final ExecutorService readerExecutor;
    private StreamResponse prefetched;
    private boolean done;

    public SseEventIterator(InputStream inputStream, A2AProtocol protocol) {
        this(inputStream, protocol, 0);
    }

    public SseEventIterator(InputStream inputStream, A2AProtocol protocol, long readTimeoutMs) {
        this(inputStream, protocol, readTimeoutMs, Long.MAX_VALUE);
    }

    public SseEventIterator(InputStream inputStream, A2AProtocol protocol, long readTimeoutMs, long maxEventBytes) {
        if (maxEventBytes <= 0) {
            throw new IllegalArgumentException("maxEventBytes must be greater than zero");
        }
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.protocol = protocol;
        this.isJsonRpc = protocol instanceof JsonRpcProtocol;
        this.readTimeoutMs = readTimeoutMs;
        this.maxEventBytes = maxEventBytes;
        this.readerExecutor = readTimeoutMs > 0
                ? Executors.newSingleThreadExecutor(new SseReaderThreadFactory())
                : null;
    }

    @Override
    public boolean hasNext() {
        if (prefetched != null) {
            return true;
        }
        if (done) {
            return false;
        }
        prefetched = readNextEvent();
        return prefetched != null;
    }

    @Override
    public StreamResponse next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        StreamResponse event = prefetched;
        prefetched = null;
        return event;
    }

    @Override
    public void close() throws IOException {
        done = true;
        closeReader();
    }

    private StreamResponse readNextEvent() {
        try {
            StringBuilder dataBuffer = new StringBuilder();
            long eventBytes = 0;
            String line;
            while ((line = readLine()) != null) {
                if (line.startsWith(":")) {
                    continue;
                }
                if (line.startsWith("data:")) {
                    eventBytes = appendData(dataBuffer, line, eventBytes);
                } else if (line.isEmpty() && dataBuffer.length() > 0) {
                    return parseEvent(dataBuffer.toString());
                }
            }
            if (dataBuffer.length() > 0) {
                done = true;
                return parseEvent(dataBuffer.toString());
            }
            done = true;
            return null;
        } catch (IOException e) {
            done = true;
            throw new RuntimeCamelException("Failed to read SSE event from stream", e);
        }
    }

    private long appendData(StringBuilder dataBuffer, String line, long eventBytes) {
        String value = line.substring("data:".length());
        if (value.startsWith(" ")) {
            value = value.substring(1);
        }
        long additionalBytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (dataBuffer.length() > 0) {
            additionalBytes++;
        }
        if (eventBytes > maxEventBytes - additionalBytes) {
            throw new RuntimeCamelException("SSE event exceeds maximum size: " + maxEventBytes + " bytes");
        }
        if (dataBuffer.length() > 0) {
            dataBuffer.append('\n');
        }
        dataBuffer.append(value);
        return eventBytes + additionalBytes;
    }

    private String readLine() throws IOException {
        if (readTimeoutMs <= 0) {
            return reader.readLine();
        }
        Future<String> future = readerExecutor.submit(reader::readLine);
        try {
            return future.get(readTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            done = true;
            closeReader();
            throw new RuntimeCamelException(
                    "SSE stream read timeout after " + readTimeoutMs + "ms without receiving data", e);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            done = true;
            throw new IOException("Interrupted while waiting for SSE event", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new RuntimeCamelException("Failed to read SSE event from stream", cause);
        }
    }

    private void closeReader() throws IOException {
        try {
            reader.close();
        } finally {
            if (readerExecutor != null) {
                readerExecutor.shutdownNow();
            }
        }
    }

    private StreamResponse parseEvent(String data) {
        if (isJsonRpc) {
            return ((JsonRpcProtocol) protocol).unwrapStreamingEvent(data);
        }
        return SseCodec.decodeData(data);
    }

    private static final class SseReaderThreadFactory implements ThreadFactory {
        private static final AtomicInteger COUNTER = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "A2ASseEventIterator-" + COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
