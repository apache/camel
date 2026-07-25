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
package org.apache.camel.impl.engine;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces CAMEL-24244: {@code UtilizationStatistics.updateSpool()} re-acquired the lock in its {@code finally} block
 * instead of releasing it, so the lock was never released and every later caller blocked forever.
 * <p>
 * A single thread cannot detect this, because the lock is a {@link java.util.concurrent.locks.ReentrantLock} that its
 * owner is free to re-acquire. The bug only shows up once a <em>different</em> thread updates the spool statistics, so
 * this test drives the spool accounting from two threads.
 * <p>
 * Two deliberate design choices keep a regression from wedging the build, because
 * {@link java.util.concurrent.locks.ReentrantLock#lock()} is uninterruptible and cannot be recovered once leaked:
 * <ul>
 * <li>a standalone {@link DefaultStreamCachingStrategy} is used instead of the context-managed one, since
 * {@code doStop()} calls {@code statistics.reset()} and would otherwise deadlock {@code CamelContext} shutdown;</li>
 * <li>the worker threads are daemons, so a permanently blocked thread cannot keep the JVM alive.</li>
 * </ul>
 * A stub converter supplies a cache that reports itself as spooled, which exercises the spool accounting directly
 * without depending on real disk spooling.
 */
class StreamCachingStrategySpoolStatisticsTest extends ContextTestSupport {

    private static final int THREADS = 2;
    private static final long SPOOLED_LENGTH = 1024L;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    @Timeout(60)
    void shouldUpdateSpoolStatisticsFromConcurrentThreads() throws Exception {
        // a body type whose only converter yields a cache that reports itself as spooled
        context.getTypeConverterRegistry().addTypeConverter(StreamCache.class, SpooledBody.class,
                new TypeConverterSupport() {
                    @Override
                    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
                        return type.cast(new SpooledCache());
                    }
                });

        DefaultStreamCachingStrategy strategy = new DefaultStreamCachingStrategy();
        strategy.setCamelContext(context);
        strategy.setEnabled(true);
        strategy.getStatistics().setStatisticsEnabled(true);
        // picks up the converter above, so cache() routes through the spool accounting
        ServiceHelper.startService(strategy);

        ExecutorService executor = Executors.newFixedThreadPool(THREADS, r -> {
            Thread t = new Thread(r, "spool-stats-test");
            t.setDaemon(true);
            return t;
        });
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch finished = new CountDownLatch(THREADS);
        AtomicInteger spooled = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                executor.execute(() -> {
                    try {
                        ready.countDown();
                        // release both threads together so they contend on the statistics lock
                        ready.await(10, TimeUnit.SECONDS);
                        Exchange exchange = new DefaultExchange(context);
                        exchange.getIn().setBody(new SpooledBody());
                        StreamCache cache = strategy.cache(exchange);
                        if (cache != null && !cache.inMemory()) {
                            spooled.incrementAndGet();
                        } else {
                            error.compareAndSet(null,
                                    new IllegalStateException("expected a spooled cache but got: " + cache));
                        }
                    } catch (Throwable t) {
                        error.compareAndSet(null, t);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            // before CAMEL-24244 the second thread blocked forever in updateSpool, so this never reached zero
            assertThat(finished.await(15, TimeUnit.SECONDS))
                    .as("both threads must finish; one blocked on the leaked spool statistics lock")
                    .isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(spooled)
                .as("both threads should have gone through the spool accounting; first error=" + error.get())
                .hasValue(THREADS);
        assertThat(strategy.getStatistics().getCacheSpoolCounter()).isEqualTo(THREADS);
        assertThat(strategy.getStatistics().getCacheSpoolSize()).isEqualTo(THREADS * SPOOLED_LENGTH);
    }

    /** Marker body type so the stub converter below is selected. */
    private static final class SpooledBody {
    }

    /** Minimal {@link StreamCache} that reports itself as spooled to disk. */
    private static final class SpooledCache implements StreamCache {

        @Override
        public void reset() {
            // noop
        }

        @Override
        public void writeTo(OutputStream os) throws IOException {
            // noop
        }

        @Override
        public StreamCache copy(Exchange exchange) {
            return new SpooledCache();
        }

        @Override
        public boolean inMemory() {
            return false;
        }

        @Override
        public long length() {
            return SPOOLED_LENGTH;
        }

        @Override
        public long position() {
            return -1;
        }
    }
}
