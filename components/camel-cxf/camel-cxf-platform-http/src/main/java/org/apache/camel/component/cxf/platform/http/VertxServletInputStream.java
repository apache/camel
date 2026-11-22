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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import io.vertx.core.Context;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;

/**
 * A blocking stream of the body of a Vert.x request, read on a worker thread. The request is paused, and each read that
 * finds no data fetches the next buffer from the event loop, so no more than one buffer of the body is held at a time.
 * <p/>
 * Created on the event loop, before the request is given to the worker thread.
 */
final class VertxServletInputStream extends ServletInputStream {

    private final Context context;
    private final HttpServerRequest request;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Deque<Buffer> queue = new ArrayDeque<>();
    private boolean ended;
    private boolean fetching;
    private Throwable failure;
    private Buffer current;
    private int position;
    private boolean closed;

    VertxServletInputStream(Context context, HttpServerRequest request) {
        this.context = context;
        this.request = request;
        request.pause();
        if (request.isEnded()) {
            // no body (or it has been read already)
            ended = true;
        } else {
            request.handler(this::onData);
            request.endHandler(v -> onEnd());
            request.exceptionHandler(this::onFailure);
        }
    }

    private void onData(Buffer buffer) {
        lock.lock();
        try {
            if (buffer.length() > 0) {
                queue.add(buffer);
            }
            fetching = false;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void onEnd() {
        lock.lock();
        try {
            ended = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void onFailure(Throwable cause) {
        lock.lock();
        try {
            failure = cause;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * The next buffer of the body, waiting for it, or null at the end of the body.
     */
    private boolean nextBuffer() throws IOException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                if (failure != null) {
                    throw new IOException("Error reading the request body: " + failure.getMessage(), failure);
                }
                if (ended || closed) {
                    return false;
                }
                if (!fetching) {
                    fetching = true;
                    context.runOnContext(v -> request.fetch(1));
                }
                try {
                    // the connection closing (or the idle timeout of the server) fails the request and wakes us up
                    changed.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new InterruptedIOException("Interrupted reading the request body");
                }
            }
            current = queue.poll();
            position = 0;
            return true;
        } finally {
            lock.unlock();
        }
    }

    private boolean ensureData() throws IOException {
        while (current == null || position >= current.length()) {
            if (!nextBuffer()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int read() throws IOException {
        if (!ensureData()) {
            return -1;
        }
        return current.getByte(position++) & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (!ensureData()) {
            return -1;
        }
        int n = Math.min(len, current.length() - position);
        current.getBytes(position, position + n, b, off);
        position += n;
        return n;
    }

    @Override
    public int available() {
        return current != null ? current.length() - position : 0;
    }

    @Override
    public boolean isFinished() {
        lock.lock();
        try {
            return ended && queue.isEmpty() && (current == null || position >= current.length());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isReady() {
        return available() > 0 || isFinished();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("Asynchronous reads are not supported");
    }

    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            queue.clear();
            current = null;
        } finally {
            lock.unlock();
        }
    }
}
