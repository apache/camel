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
package org.apache.camel.component.mtconnect;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Closes a response body when the agent sends nothing for a bound, which unblocks the thread reading it. The JDK HTTP
 * client has no read timeout, and a frozen agent or a black-holed link raises no connection error. Each read gets its
 * own {@link Watch}, so that concurrent reads do not share a bound.
 */
final class ReadDeadline {

    private static final Logger LOG = LoggerFactory.getLogger(ReadDeadline.class);

    private final ScheduledExecutorService scheduler;
    private final long millis;

    ReadDeadline(ScheduledExecutorService scheduler, long millis) {
        this.scheduler = scheduler;
        this.millis = millis;
    }

    /** Starts the bound for the given body. */
    Watch watch(Closeable body) {
        return new Watch(body);
    }

    /** The bound of one read: it elapses when no data arrived for the bound since the last {@link #touch()}. */
    final class Watch implements AutoCloseable {

        private final Closeable body;
        private volatile long lastData = System.nanoTime();
        private volatile ScheduledFuture<?> pending;
        private volatile boolean closed;
        private volatile boolean expired;

        private Watch(Closeable body) {
            this.body = body;
            pending = scheduler.schedule(this::check, millis, TimeUnit.MILLISECONDS);
        }

        /** Data arrived: the bound starts again. */
        void touch() {
            lastData = System.nanoTime();
        }

        /** The bound in milliseconds. */
        long bound() {
            return millis;
        }

        /** Whether the bound elapsed and closed the body. */
        boolean expired() {
            return expired;
        }

        @Override
        public void close() {
            closed = true;
            ScheduledFuture<?> future = pending;
            if (future != null) {
                future.cancel(false);
            }
        }

        private void check() {
            if (closed) {
                return;
            }
            long remaining = millis - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastData);
            if (remaining > 0) {
                pending = scheduler.schedule(this::check, remaining, TimeUnit.MILLISECONDS);
                return;
            }
            expired = true;
            try {
                body.close();
            } catch (IOException e) {
                LOG.debug("Closing a silent agent response failed", e);
            }
        }
    }
}
