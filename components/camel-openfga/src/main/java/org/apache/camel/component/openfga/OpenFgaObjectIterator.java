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
package org.apache.camel.component.openfga;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import dev.openfga.sdk.api.client.JsonSerializer;
import dev.openfga.sdk.api.client.SdkTypeToken;
import dev.openfga.sdk.api.model.ListObjectsRequest;
import dev.openfga.sdk.api.model.StreamResult;
import dev.openfga.sdk.api.model.StreamedListObjectsResponse;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.util.IOHelper;

/**
 * A lazy, closeable object stream. Iteration is single-threaded; close may be called from another thread.
 * <p>
 * No result list is materialized. A response is consumed only as its caller advances the iterator, and reaching the
 * configured result bound fails rather than silently presenting a partial answer as a complete query.
 */
public final class OpenFgaObjectIterator implements Iterator<String>, Closeable {

    private static final JsonSerializer SERIALIZER = JsonSerializer.createDefault();
    private static final SdkTypeToken<StreamResult<StreamedListObjectsResponse>> RESULT_TYPE = new SdkTypeToken<>() {
    };

    private final OpenFgaStreamingClient client;
    private final String storeId;
    private final ListObjectsRequest request;
    private final ScheduledExecutorService scheduler;
    private final long timeoutMillis;
    private final int maxResults;
    private final int maxLineLength;
    private final Consumer<OpenFgaObjectIterator> onClose;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<InputStream> body = new AtomicReference<>();
    private volatile CompletableFuture<HttpResponse<InputStream>> response;
    private volatile boolean complete;
    private BufferedReader reader;
    private RuntimeCamelException failure;
    private String next;
    private int received;

    OpenFgaObjectIterator(OpenFgaStreamingClient client, String storeId, ListObjectsRequest request,
                          ScheduledExecutorService scheduler, long timeoutMillis, int maxResults, int maxLineLength,
                          Consumer<OpenFgaObjectIterator> onClose) {
        this.client = client;
        this.storeId = storeId;
        this.request = request;
        this.scheduler = scheduler;
        this.timeoutMillis = timeoutMillis;
        this.maxResults = maxResults;
        this.maxLineLength = maxLineLength;
        this.onClose = onClose;
    }

    void completeEmpty() {
        complete = true;
        close();
    }

    @Override
    public boolean hasNext() {
        if (failure != null) {
            throw failure;
        }
        if (complete) {
            return false;
        }
        if (closed.get()) {
            throw new IllegalStateException("The OpenFGA object stream was closed before completion");
        }
        if (next != null) {
            return true;
        }
        try {
            if (reader == null) {
                open();
            }
            AtomicBoolean reading = new AtomicBoolean(true);
            AtomicBoolean timedOut = new AtomicBoolean();
            var deadline = scheduler.schedule(() -> {
                if (reading.compareAndSet(true, false)) {
                    timedOut.set(true);
                    IOHelper.close(body.get());
                }
            }, timeoutMillis, TimeUnit.MILLISECONDS);
            try {
                while (next == null) {
                    String line = readLine();
                    if (timedOut.get()) {
                        throw new HttpTimeoutException("Timed out reading an OpenFGA object stream");
                    }
                    if (closed.get()) {
                        throw new IOException("The OpenFGA object stream was closed while reading");
                    }
                    if (line == null) {
                        if (!reading.compareAndSet(true, false)) {
                            throw new HttpTimeoutException("Timed out reading an OpenFGA object stream");
                        }
                        complete = true;
                        close();
                        return false;
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    StreamResult<StreamedListObjectsResponse> result = SERIALIZER.readValue(line, RESULT_TYPE);
                    if (result.getError() != null) {
                        throw new IOException("OpenFGA object stream failed: " + result.getError().getMessage());
                    }
                    if (result.getResult() == null || result.getResult().getObject() == null) {
                        throw new IOException("OpenFGA object stream returned no object");
                    }
                    if (++received > maxResults) {
                        throw new IOException("OpenFGA object stream exceeded streamMaxResults=" + maxResults);
                    }
                    next = result.getResult().getObject();
                }
                if (!reading.compareAndSet(true, false)) {
                    throw new HttpTimeoutException("Timed out reading an OpenFGA object stream");
                }
                return true;
            } catch (IOException e) {
                if (timedOut.get()) {
                    throw new HttpTimeoutException("Timed out reading an OpenFGA object stream");
                }
                throw e;
            } finally {
                reading.set(false);
                deadline.cancel(false);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fail(e);
        } catch (Exception e) {
            throw fail(e);
        }
    }

    private void open() throws Exception {
        response = client.open(storeId, request);
        // A close racing with header arrival must also close the late response body.
        response.whenComplete((value, error) -> {
            if (closed.get() && value != null) {
                IOHelper.close(value.body());
            }
        });
        if (closed.get()) {
            response.cancel(true);
            throw new IOException("The OpenFGA object stream was closed while opening");
        }
        HttpResponse<InputStream> value;
        try {
            value = response.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new HttpTimeoutException("Timed out opening an OpenFGA object stream");
        } catch (ExecutionException e) {
            throw new IOException("Could not open an OpenFGA object stream", e.getCause());
        }
        body.set(value.body());
        if (closed.get()) {
            IOHelper.close(value.body());
            throw new IOException("The OpenFGA object stream was closed while opening");
        }
        if (value.statusCode() < 200 || value.statusCode() >= 300) {
            throw new IOException("OpenFGA object stream answered HTTP " + value.statusCode());
        }
        reader = new BufferedReader(new InputStreamReader(value.body(), StandardCharsets.UTF_8));
    }

    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') {
                return line.toString();
            }
            if (ch != '\r') {
                if (line.length() >= maxLineLength) {
                    throw new IOException("OpenFGA object stream exceeded streamMaxLineLength=" + maxLineLength);
                }
                line.append((char) ch);
            }
        }
        return line.isEmpty() ? null : line.toString();
    }

    private RuntimeCamelException fail(Exception error) {
        failure = new RuntimeCamelException("OpenFGA object stream did not complete", error);
        close();
        return failure;
    }

    @Override
    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        String value = next;
        next = null;
        return value;
    }

    /** Whether the server's response was completely consumed, rather than cancelled or failed. */
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            if (response != null) {
                response.cancel(true);
            }
            IOHelper.close(body.get());
            onClose.accept(this);
        }
    }
}
