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
package org.apache.camel.component.file.strategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.KeyValueIdempotentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The idempotent read locks run their pending delayed release tasks when they stop, instead of waiting for the delay or
 * dropping the tasks when the thread pool is shut down.
 */
class FileIdempotentReadLockReleaseOnStopTest extends ContextTestSupport {

    // far longer than the 30 seconds the read lock waits for its thread pool when it stops
    private static final int LONG_DELAY = 120000;

    private final CountingRepository repo = new CountingRepository();
    // the route fails the first file it routes
    private final AtomicBoolean failFirst = new AtomicBoolean();
    private final AtomicInteger routed = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @ParameterizedTest
    @ValueSource(strings = { "idempotent", "idempotent-changed" })
    void testRollbackReleaseRunsWhenTheRouteStops(String readLock) throws Exception {
        Path file = testFile("hello.txt");
        Files.writeString(file, "Hello World");
        failFirst.set(true);
        startRoute("readLock=" + readLock + "&readLockCheckInterval=100&readLockIdempotentReleaseAsync=true"
                   + "&readLockIdempotentReleaseDelay=" + LONG_DELAY);

        // the route fails, and the removal of the key is scheduled after the delay
        await().atMost(10, TimeUnit.SECONDS).until(() -> routed.get() >= 1);
        assertThat(routed.get()).isEqualTo(1);
        assertThat(repo.keys).hasSize(1);

        // the stop runs the removal at once
        CompletableFuture<Void> stop = stopRouteAsync();
        await().atMost(10, TimeUnit.SECONDS).until(stop::isDone);
        assertThat(stop).isCompleted();
        assertThat(repo.keys).isEmpty();
        assertThat(repo.removed.get()).isEqualTo(1);

        // so the file is retried after a restart
        context.getRouteController().startRoute("foo");
        await().atMost(10, TimeUnit.SECONDS).until(() -> !Files.exists(file));
        assertThat(routed.get()).isEqualTo(2);
    }

    @Test
    void testCommitReleaseRunsWhenTheRouteStops() throws Exception {
        Path file = testFile("hello.txt");
        Files.writeString(file, "Hello World");
        startRoute("readLock=idempotent&readLockRemoveOnCommit=true&readLockIdempotentReleaseAsync=true"
                   + "&readLockIdempotentReleaseDelay=" + LONG_DELAY);

        // the file is processed and deleted, the removal of the key is scheduled after the delay
        await().atMost(10, TimeUnit.SECONDS).until(() -> routed.get() == 1 && !Files.exists(file));
        assertThat(repo.keys).hasSize(1);

        CompletableFuture<Void> stop = stopRouteAsync();
        await().atMost(10, TimeUnit.SECONDS).until(stop::isDone);
        assertThat(stop).isCompleted();
        assertThat(repo.keys).isEmpty();

        // a new file with the same name is processed after a restart
        context.getRouteController().startRoute("foo");
        Files.writeString(file, "Bye World");
        await().atMost(10, TimeUnit.SECONDS).until(() -> routed.get() == 2 && !Files.exists(file));
    }

    @Test
    void testReleaseThatRanIsNotRunAgainWhenTheRouteStops() throws Exception {
        Path file = testFile("hello.txt");
        Files.writeString(file, "Hello World");
        failFirst.set(true);
        startRoute("readLock=idempotent&readLockIdempotentReleaseAsync=true&readLockIdempotentReleaseDelay=100");

        // the route fails, the thread pool removes the key after the delay, and the file is processed again
        await().atMost(10, TimeUnit.SECONDS).until(() -> routed.get() == 2 && !Files.exists(file));

        context.getRouteController().stopRoute("foo");
        assertThat(repo.removed.get()).isEqualTo(1);
        assertThat(routed.get()).isEqualTo(2);
    }

    @Test
    void testChangedKeyIsRemovedWhenTheCleanupAfterTheChangedCheckFails() throws Exception {
        // shorter than readLockMinLength: the changed check fails after the key is added, and its cleanup fails once
        Path file = testFile("hello.txt");
        Files.writeString(file, "Hello");
        repo.failRemove.set(true);
        startRoute("readLock=idempotent-changed&readLockMinLength=100&readLockTimeout=400&readLockCheckInterval=100");

        // the abort that follows removes the key
        await().atMost(10, TimeUnit.SECONDS).until(() -> repo.removed.get() >= 1);

        // so the file is processed once it is ready
        Files.writeString(file, "x".repeat(200), StandardOpenOption.APPEND);
        await().atMost(10, TimeUnit.SECONDS).until(() -> routed.get() == 1 && !Files.exists(file));
    }

    private void startRoute(String options) throws Exception {
        context.getRegistry().bind("myRepo", repo);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&delete=true&idempotentRepository=#myRepo&" + options))
                        .routeId("foo")
                        .process(exchange -> {
                            if (routed.incrementAndGet() == 1 && failFirst.get()) {
                                throw new IllegalStateException("Forced");
                            }
                        })
                        .to("mock:result");
            }
        });
        context.start();
    }

    private CompletableFuture<Void> stopRouteAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                context.getRouteController().stopRoute("foo");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * Keeps the keys it holds, counts the removals that succeed, and can fail one removal.
     */
    private static final class CountingRepository extends KeyValueIdempotentRepository {
        private final Set<String> keys = ConcurrentHashMap.newKeySet();
        private final AtomicInteger removed = new AtomicInteger();
        private final AtomicBoolean failRemove = new AtomicBoolean();

        @Override
        public boolean add(String key) {
            boolean answer = super.add(key);
            if (answer) {
                keys.add(key);
            }
            return answer;
        }

        @Override
        public boolean remove(String key) {
            if (failRemove.compareAndSet(true, false)) {
                throw new IllegalStateException("Forced remove failure");
            }
            boolean answer = super.remove(key);
            keys.remove(key);
            removed.incrementAndGet();
            return answer;
        }
    }
}
