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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import dev.openfga.sdk.api.model.ListObjectsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenFgaObjectIteratorTest {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicInteger closed = new AtomicInteger();

    @AfterEach
    void stop() {
        scheduler.shutdownNow();
    }

    @Test
    void opensLazilyAndCompletesOnlyAfterExhaustion() {
        AtomicInteger opens = new AtomicInteger();
        TrackingInputStream body = body(record("document:a") + record("document:b"));
        try (var iterator = iterator((store, request) -> {
            opens.incrementAndGet();
            assertThat(store).isEqualTo("store");
            return response(body, 200);
        }, 1000, 10, 1000)) {
            assertThat(opens).hasValue(0);
            assertThat(iterator.next()).isEqualTo("document:a");
            assertThat(opens).hasValue(1);
            assertThat(iterator.isComplete()).isFalse();
            assertThat(body.closed).isFalse();
            assertThat(iterator.next()).isEqualTo("document:b");
            assertThat(iterator.hasNext()).isFalse();
            assertThat(iterator.isComplete()).isTrue();
            assertThat(iterator.hasNext()).isFalse();
        }
        assertThat(body.closed).isTrue();
        assertThat(closed).hasValue(1);
    }

    @Test
    void readingOneObjectDoesNotMaterializeTheRemainingResponse() {
        TrackingInputStream body = body(record("document:a").repeat(10000));
        int originalSize = body.available();
        try (var iterator = iterator((store, request) -> response(body, 200), 1000, 10000, 1000)) {
            assertThat(iterator.next()).isEqualTo("document:a");
            assertThat(body.available()).isGreaterThan(originalSize - 32768);
            assertThat(iterator.isComplete()).isFalse();
        }
    }

    @Test
    void closeBeforeIterationDoesNotOpenRequest() {
        AtomicInteger opens = new AtomicInteger();
        var iterator = iterator((store, request) -> {
            opens.incrementAndGet();
            return new CompletableFuture<>();
        }, 1000, 10, 1000);
        iterator.close();
        iterator.close();
        assertThat(opens).hasValue(0);
        assertThat(closed).hasValue(1);
        assertThatThrownBy(iterator::hasNext).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failureAfterPartialResultsIsVisibleAndClosesBody() {
        TrackingInputStream body = body(record("document:a") + "{\"error\":{\"code\":13,\"message\":\"failed\"}}\n");
        try (var iterator = iterator((store, request) -> response(body, 200), 1000, 10, 1000)) {
            assertThat(iterator.next()).isEqualTo("document:a");
            assertThatThrownBy(iterator::hasNext).hasRootCauseMessage("OpenFGA object stream failed: failed");
            assertThat(iterator.isComplete()).isFalse();
            assertThat(body.closed).isTrue();
            assertThatThrownBy(iterator::hasNext).hasRootCauseMessage("OpenFGA object stream failed: failed");
        }
    }

    @Test
    void limitFailsInsteadOfSilentlyReturningPartialAnswer() {
        TrackingInputStream body = body(record("document:a") + record("document:b"));
        try (var iterator = iterator((store, request) -> response(body, 200), 1000, 1, 1000)) {
            assertThat(iterator.next()).isEqualTo("document:a");
            assertThatThrownBy(iterator::hasNext).hasRootCauseMessage("OpenFGA object stream exceeded streamMaxResults=1");
            assertThat(body.closed).isTrue();
            assertThat(iterator.isComplete()).isFalse();
        }
    }

    @Test
    void oversizedRecordFailsAndCloses() {
        TrackingInputStream body = body(record("document:a"));
        try (var iterator = iterator((store, request) -> response(body, 200), 1000, 10, 8)) {
            assertThatThrownBy(iterator::hasNext).hasRootCauseMessage("OpenFGA object stream exceeded streamMaxLineLength=8");
            assertThat(body.closed).isTrue();
        }
    }

    @Test
    void malformedResponseFailsAndCloses() {
        TrackingInputStream body = body("{}\n");
        try (var iterator = iterator((store, request) -> response(body, 200), 1000, 10, 1000)) {
            assertThatThrownBy(iterator::hasNext).hasRootCauseMessage("OpenFGA object stream returned no object");
            assertThat(body.closed).isTrue();
        }
    }

    @Test
    void httpFailureClosesResponse() {
        TrackingInputStream body = body("unavailable");
        try (var iterator = iterator((store, request) -> response(body, 503), 1000, 10, 1000)) {
            assertThatThrownBy(iterator::hasNext).hasRootCauseMessage("OpenFGA object stream answered HTTP 503");
            assertThat(body.closed).isTrue();
        }
    }

    @Test
    void headerTimeoutCancelsRequest() {
        CompletableFuture<HttpResponse<InputStream>> response = new CompletableFuture<>();
        try (var iterator = iterator((store, request) -> response, 50, 10, 1000)) {
            assertThatThrownBy(iterator::hasNext).hasRootCauseInstanceOf(HttpTimeoutException.class);
            assertThat(response.isCancelled()).isTrue();
        }
    }

    @Test
    void blockedBodyIsClosedOnTimeout() {
        BlockingInputStream body = new BlockingInputStream();
        try (var iterator = iterator((store, request) -> response(body, 200), 50, 10, 1000)) {
            assertThatThrownBy(iterator::hasNext).hasRootCauseInstanceOf(HttpTimeoutException.class);
            assertThat(body.closed.get()).isTrue();
        }
    }

    @Test
    void explicitCloseUnblocksPendingRead() throws Exception {
        BlockingInputStream body = new BlockingInputStream();
        try (var iterator = iterator((store, request) -> response(body, 200), 5000, 10, 1000)) {
            CompletableFuture<Void> read = CompletableFuture
                    .runAsync(() -> assertThatThrownBy(iterator::hasNext).hasRootCauseInstanceOf(IOException.class));
            assertThat(body.reading.await(2, TimeUnit.SECONDS)).isTrue();
            iterator.close();
            read.get(2, TimeUnit.SECONDS);
            assertThat(body.closed.get()).isTrue();
        }
    }

    @Test
    void interruptedHeaderWaitCancelsAndRestoresInterrupt() {
        CompletableFuture<HttpResponse<InputStream>> response = new CompletableFuture<>();
        try (var iterator = iterator((store, request) -> response, 1000, 10, 1000)) {
            Thread.currentThread().interrupt();
            try {
                assertThatThrownBy(iterator::hasNext).hasRootCauseInstanceOf(InterruptedException.class);
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
                assertThat(response.isCancelled()).isTrue();
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void timeoutDoesNotRunWhileCallerProcessesObject() throws Exception {
        TrackingInputStream body = body(record("document:a") + record("document:b"));
        try (var iterator = iterator((store, request) -> response(body, 200), 50, 10, 1000)) {
            assertThat(iterator.next()).isEqualTo("document:a");
            // A marker on the same scheduler demonstrates that the read deadline has elapsed without sleeping.
            scheduler.schedule(() -> {
            }, 100, TimeUnit.MILLISECONDS).get(2, TimeUnit.SECONDS);
            assertThat(body.closed).isFalse();
            assertThat(iterator.next()).isEqualTo("document:b");
        }
    }

    private OpenFgaObjectIterator iterator(OpenFgaStreamingClient client, long timeout, int limit, int lineLimit) {
        return new OpenFgaObjectIterator(
                client, "store", new ListObjectsRequest(), scheduler, timeout, limit, lineLimit,
                value -> closed.incrementAndGet());
    }

    private static String record(String object) {
        return "{\"result\":{\"object\":\"" + object + "\"}}\n";
    }

    private static TrackingInputStream body(String text) {
        return new TrackingInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<HttpResponse<InputStream>> response(InputStream body, int status) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.body()).thenReturn(body);
        when(response.statusCode()).thenReturn(status);
        return CompletableFuture.completedFuture(response);
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        TrackingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static class BlockingInputStream extends InputStream {
        private final CountDownLatch reading = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public int read() throws IOException {
            reading.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IOException("Test stream was never closed");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            throw new IOException("closed");
        }

        @Override
        public void close() {
            closed.set(true);
            release.countDown();
        }
    }
}
