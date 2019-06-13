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

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.apache.camel.AsyncCallback;
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
            createdWorkers.incrementAndGet();
            return new Worker(DefaultReactiveExecutor.this);
        }
    });

    // use for statistics so we have insights at runtime
    private final AtomicInteger createdWorkers = new AtomicInteger();
    private final AtomicInteger runningWorkers = new AtomicInteger();
    private final AtomicLong pendingTasks = new AtomicLong();

    @Override
    public void scheduleMain(Runnable runnable, String description) {
        if (description != null) {
            runnable = describe(runnable, description);
        }
        workers.get().schedule(runnable, true, true, false);
    }

    @Override
    public void schedule(Runnable runnable, String description) {
        if (description != null) {
            runnable = describe(runnable, description);
        }
        workers.get().schedule(runnable, true, false, false);
    }

    @Override
    public void scheduleSync(Runnable runnable, String description) {
        if (description != null) {
            runnable = describe(runnable, description);
        }
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
    public long getPendingTasks() {
        return pendingTasks.get();
    }

    @Override
    public void callback(AsyncCallback callback) {
        schedule(new Runnable() {
            @Override
            public void run() {
                callback.done(false);
            }
            @Override
            public String toString() {
                return "Callback[" + callback + "]";
            }
        });
    }

    private static Runnable describe(Runnable runnable, String description) {
        return new Runnable() {
            @Override
            public void run() {
                runnable.run();
            }
            @Override
            public String toString() {
                return description;
            }
        };
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    private static class Worker {

        private final DefaultReactiveExecutor executor;
        private volatile LinkedList<Runnable> queue = new LinkedList<>();
        private volatile LinkedList<LinkedList<Runnable>> back;
        private volatile boolean running;

        public Worker(DefaultReactiveExecutor executor) {
            this.executor = executor;
        }

        void schedule(Runnable runnable, boolean first, boolean main, boolean sync) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Schedule [first={}, main={}, sync={}]: {}", first, main, sync, runnable);
            }
            if (main) {
                if (!queue.isEmpty()) {
                    if (back == null) {
                        back = new LinkedList<>();
                    }
                    back.push(queue);
                    queue = new LinkedList<>();
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
//                Thread thread = Thread.currentThread();
//                String name = thread.getName();
                try {
                    for (;;) {
                        final Runnable polled = queue.poll();
                        if (polled == null) {
                            if (back != null && !back.isEmpty()) {
                                queue = back.poll();
                                continue;
                            } else {
                                break;
                            }
                        }
                        try {
                            executor.pendingTasks.decrementAndGet();
//                            thread.setName(name + " - " + polled.toString());
                            if (LOG.isTraceEnabled()) {
                                LOG.trace("Running: {}", runnable);
                            }
                            polled.run();
                        } catch (Throwable t) {
                            LOG.warn("Error executing reactive work due to " + t.getMessage() + ". This exception is ignored.", t);
                        }
                    }
                } finally {
//                    thread.setName(name);
                    running = false;
                    executor.runningWorkers.decrementAndGet();
                }
            } else {
                LOG.debug("Queuing reactive work: {}", runnable);
            }
        }

        boolean executeFromQueue() {
            final Runnable polled = queue != null ? queue.poll() : null;
            if (polled == null) {
                return false;
            }
            Thread thread = Thread.currentThread();
            String name = thread.getName();
            try {
                executor.pendingTasks.decrementAndGet();
                thread.setName(name + " - " + polled.toString());
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Running: {}", polled);
                }
                polled.run();
            } catch (Throwable t) {
                // should not happen
                LOG.warn("Error executing reactive work due to " + t.getMessage() + ". This exception is ignored.", t);
            } finally {
                thread.setName(name);
            }
            return true;
        }

    }

}
