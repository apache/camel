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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The read lock release tasks that an idempotent read lock strategy scheduled with readLockIdempotentReleaseAsync and
 * readLockIdempotentReleaseDelay, and that have not run yet. When the strategy stops it runs them at once: shutting
 * down the thread pool would drop them, and their keys would stay in the idempotent repository, so the files would
 * never be picked up again.
 */
final class DelayedReadLockReleaseTasks {

    private static final Logger LOG = LoggerFactory.getLogger(DelayedReadLockReleaseTasks.class);

    private final Set<Task> pending = ConcurrentHashMap.newKeySet();

    /**
     * Schedules the release task to run after the delay.
     */
    void schedule(ScheduledExecutorService executorService, Runnable release, long delay) {
        Task task = new Task(release);
        pending.add(task);
        task.future = executorService.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Runs the release tasks that have not run yet in the calling thread. A task runs only once, here or in the thread
     * pool.
     */
    void runPending() {
        if (pending.isEmpty()) {
            return;
        }
        LOG.info("Releasing {} idempotent read locks now, before their readLockIdempotentReleaseDelay, as the read lock"
                 + " is stopping",
                pending.size());
        for (Task task : pending) {
            try {
                task.runNow();
            } catch (Exception e) {
                LOG.warn("Error during releasing exclusive read lock on stop. This exception is ignored.", e);
            }
        }
    }

    private final class Task implements Runnable {

        private final Runnable release;
        private final AtomicBoolean started = new AtomicBoolean();
        private volatile Future<?> future;

        Task(Runnable release) {
            this.release = release;
        }

        @Override
        public void run() {
            if (started.compareAndSet(false, true)) {
                try {
                    release.run();
                } finally {
                    pending.remove(this);
                }
            }
        }

        void runNow() {
            Future<?> f = future;
            if (f != null) {
                f.cancel(false);
            }
            run();
        }
    }
}
