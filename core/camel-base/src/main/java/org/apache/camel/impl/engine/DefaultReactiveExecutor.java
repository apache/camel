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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.camel.StaticService;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ReactiveExecutor}.
 */
@ManagedResource(description = "Managed ReactiveExecutor")
public class DefaultReactiveExecutor extends ServiceSupport implements ReactiveExecutor, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultReactiveExecutor.class);

    private final ThreadLocal<Worker> workers = ThreadLocal.withInitial(new Supplier<Worker>() {
        @Override
        public Worker get() {
            int number = createdWorkers.incrementAndGet();
            return new Worker(number, DefaultReactiveExecutor.this);
        }
    });

    // use for statistics so we have insights at runtime
    private final AtomicInteger createdWorkers = new AtomicInteger();
    private final AtomicInteger runningWorkers = new AtomicInteger();
    private final AtomicInteger pendingTasks = new AtomicInteger();

    @Override
    public void schedule(Runnable runnable) {
        workers.get().schedule(runnable, true, false, false);
    }

    @Override
    public void scheduleMain(Runnable runnable) {
        workers.get().schedule(runnable, true, true, false);
    }

    @Override
    public void scheduleSync(Runnable runnable) {
        workers.get().schedule(runnable, false, true, true);
    }

    @Override
    public boolean executeFromQueue() {
        return workers.get().executeFromQueue();
    }

    @ManagedAttribute(description = "Number of created workers")
    public int getCreatedWorkers() {
        return createdWorkers.get();
    }

    @ManagedAttribute(description = "Number of running workers")
    public int getRunningWorkers() {
        return runningWorkers.get();
    }

    @ManagedAttribute(description = "Number of pending tasks")
    public int getPendingTasks() {
        return pendingTasks.get();
    }

    @Override
    protected void doStop() throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping DefaultReactiveExecutor [createdWorkers: {}, runningWorkers: {}, pendingTasks: {}]",
                    getCreatedWorkers(), getRunningWorkers(), getPendingTasks());
        }
    }

    private static class Worker {

        private final int number;
        private final DefaultReactiveExecutor executor;
        private volatile Deque<Runnable> queue = new ArrayDeque<>();
        private volatile Deque<Deque<Runnable>> back;
        private volatile boolean running;

        public Worker(int number, DefaultReactiveExecutor executor) {
            this.number = number;
            this.executor = executor;
        }

        void schedule(Runnable runnable, boolean first, boolean main, boolean sync) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Schedule [first={}, main={}, sync={}]: {}", first, main, sync, runnable);
            }
            if (main) {
                if (!queue.isEmpty()) {
                    if (back == null) {
                        back = new ArrayDeque<>();
                    }
                    back.push(queue);
                    queue = new ArrayDeque<>();
                }
            }
            if (first) {
                queue.addFirst(runnable);
                executor.pendingTasks.incrementAndGet();
            } else {
                queue.addLast(runnable);
                executor.pendingTasks.incrementAndGet();
            }
            if (!running || sync) {
                running = true;
                executor.runningWorkers.incrementAndGet();
                try {
                    for (;;) {
                        final Runnable polled = queue.pollFirst();
                        if (polled == null) {
                            if (back != null && !back.isEmpty()) {
                                queue = back.pollFirst();
                                continue;
                            } else {
                                break;
                            }
                        }
                        try {
                            executor.pendingTasks.decrementAndGet();
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Worker #{} running: {}", number, runnable);
                            }
                            polled.run();
                        } catch (Throwable t) {
                            LOG.warn("Error executing reactive work due to " + t.getMessage() + ". This exception is ignored.", t);
                        }
                    }
                } finally {
                    running = false;
                    executor.runningWorkers.decrementAndGet();
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Queuing reactive work: {}", runnable);
                }
            }
        }

        boolean executeFromQueue() {
            final Runnable polled = queue != null ? queue.pollFirst() : null;
            if (polled == null) {
                return false;
            }
            try {
                executor.pendingTasks.decrementAndGet();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Running: {}", polled);
                }
                polled.run();
            } catch (Throwable t) {
                // should not happen
                LOG.warn("Error executing reactive work due to " + t.getMessage() + ". This exception is ignored.", t);
            }
            return true;
        }

    }

}
