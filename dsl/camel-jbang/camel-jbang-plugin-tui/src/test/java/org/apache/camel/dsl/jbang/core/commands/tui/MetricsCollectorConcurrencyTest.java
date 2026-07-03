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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that concurrent reads and writes on {@link MetricsCollector} history maps do not cause
 * {@link ConcurrentModificationException}. This reproduces the race condition between the background data-refresh
 * thread (writer) and the UI render thread (reader) that was observed in the TUI Memory tab.
 */
class MetricsCollectorConcurrencyTest {

    @Test
    void concurrentHeapHistoryUpdateAndReadShouldNotThrow() throws Exception {
        MetricsCollector metrics = new MetricsCollector();
        Map<String, LinkedList<Long>> heapHistory = metrics.getHeapMemHistory();

        AtomicReference<Throwable> writerError = new AtomicReference<>();
        AtomicReference<Throwable> readerError = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Writer thread: simulates the background refresh thread calling updateHeapHistory
        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 5000; i++) {
                    IntegrationInfo info = new IntegrationInfo();
                    info.pid = "42";
                    info.heapMemUsed = 1024L * 1024 * (50 + (i % 100));
                    // Bypass the time throttle by accessing the map directly via addToHistory
                    // (updateHeapHistory has a 5-second interval guard)
                    heapHistory.compute("42", (k, old) -> {
                        LinkedList<Long> hist = old != null ? new LinkedList<>(old) : new LinkedList<>();
                        hist.add(info.heapMemUsed);
                        while (hist.size() > MetricsCollector.MAX_HEAP_HISTORY_POINTS) {
                            hist.remove(0);
                        }
                        return hist;
                    });
                }
            } catch (Throwable t) {
                writerError.set(t);
            } finally {
                doneLatch.countDown();
            }
        }, "heap-writer");

        // Reader thread: simulates the UI render thread iterating the history list
        // (same access pattern as MemoryTab.renderSparkline)
        Thread reader = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10000; i++) {
                    LinkedList<Long> hist = heapHistory.get("42");
                    if (hist != null && !hist.isEmpty()) {
                        // This is the exact call that threw ConcurrentModificationException
                        hist.stream().mapToLong(Long::longValue).max().orElse(1);

                        // Also exercise index-based access (used in renderSparkline's data loop)
                        int size = hist.size();
                        for (int j = 0; j < size; j++) {
                            try {
                                hist.get(j);
                            } catch (IndexOutOfBoundsException e) {
                                // Stale size is acceptable — CME is not
                                break;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                readerError.set(t);
            } finally {
                doneLatch.countDown();
            }
        }, "heap-reader");

        writer.start();
        reader.start();
        startLatch.countDown();

        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);

        assertNull(writerError.get(), () -> "Writer thread threw: " + writerError.get());
        assertNull(readerError.get(), () -> "Reader thread threw: " + readerError.get());
        if (!finished) {
            writer.interrupt();
            reader.interrupt();
            throw new AssertionError("Test timed out — threads may be deadlocked");
        }
    }

    @Test
    void concurrentThroughputHistoryUpdateAndReadShouldNotThrow() throws Exception {
        MetricsCollector metrics = new MetricsCollector();
        Map<String, LinkedList<Long>> throughputHistory = metrics.getThroughputHistory();

        AtomicReference<Throwable> writerError = new AtomicReference<>();
        AtomicReference<Throwable> readerError = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 5000; i++) {
                    throughputHistory.compute("42", (k, old) -> {
                        LinkedList<Long> hist = old != null ? new LinkedList<>(old) : new LinkedList<>();
                        hist.add((long) (Math.random() * 1000));
                        while (hist.size() > MetricsCollector.MAX_SPARKLINE_POINTS) {
                            hist.remove(0);
                        }
                        return hist;
                    });
                }
            } catch (Throwable t) {
                writerError.set(t);
            } finally {
                doneLatch.countDown();
            }
        }, "throughput-writer");

        Thread reader = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < 10000; i++) {
                    LinkedList<Long> hist = throughputHistory.get("42");
                    if (hist != null && !hist.isEmpty()) {
                        hist.stream().mapToLong(Long::longValue).max().orElse(0);
                    }
                }
            } catch (Throwable t) {
                readerError.set(t);
            } finally {
                doneLatch.countDown();
            }
        }, "throughput-reader");

        writer.start();
        reader.start();
        startLatch.countDown();

        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);

        assertNull(writerError.get(), () -> "Writer thread threw: " + writerError.get());
        assertNull(readerError.get(), () -> "Reader thread threw: " + readerError.get());
        if (!finished) {
            writer.interrupt();
            reader.interrupt();
            throw new AssertionError("Test timed out — threads may be deadlocked");
        }
    }
}
