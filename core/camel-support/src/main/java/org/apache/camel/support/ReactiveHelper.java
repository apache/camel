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
package org.apache.camel.support;

import java.util.LinkedList;

import org.apache.camel.AsyncCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A basic reactive engine that uses a worker pool to process tasks.
 */
@Deprecated
public final class ReactiveHelper {

    private static final ThreadLocal<Worker> WORKERS = ThreadLocal.withInitial(Worker::new);

    private static final Logger LOG = LoggerFactory.getLogger(ReactiveHelper.class);

    private ReactiveHelper() {
    }

    public static void scheduleMain(Runnable runnable) {
        WORKERS.get().schedule(runnable, true, true, false);
    }

    public static void scheduleSync(Runnable runnable) {
        WORKERS.get().schedule(runnable, true, true, true);
    }

    public static void scheduleMain(Runnable runnable, String description) {
        WORKERS.get().schedule(describe(runnable, description), true, true, false);
    }

    public static void schedule(Runnable runnable) {
        WORKERS.get().schedule(runnable, true, false, false);
    }

    public static void schedule(Runnable runnable, String description) {
        WORKERS.get().schedule(describe(runnable, description), true, false, false);
    }

    /**
     * @deprecated not in use
     */
    @Deprecated
    public static void scheduleLast(Runnable runnable, String description) {
        WORKERS.get().schedule(describe(runnable, description), false, false, false);
    }

    public static void scheduleSync(Runnable runnable, String description) {
        WORKERS.get().schedule(describe(runnable, description), false, true, true);
    }

    public static boolean executeFromQueue() {
        return WORKERS.get().executeFromQueue();
    }

    public static void callback(AsyncCallback callback) {
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

    private static class Worker {

        private volatile LinkedList<Runnable> queue = new LinkedList<>();
        private volatile LinkedList<LinkedList<Runnable>> back;
        private volatile boolean running;

        public void schedule(Runnable runnable, boolean first, boolean main, boolean sync) {
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
            } else {
                queue.addLast(runnable);
            }
            if (!running || sync) {
                running = true;
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
//                            thread.setName(name + " - " + polled.toString());
                            polled.run();
                        } catch (Throwable t) {
                            LOG.warn("Error executing reactive work due to " + t.getMessage() + ". This exception is ignored.", t);
                        }
                    }
                } finally {
//                    thread.setName(name);
                    running = false;
                }
            } else {
                LOG.debug("Queuing reactive work: {}", runnable);
            }
        }

        public boolean executeFromQueue() {
            final Runnable polled = queue != null ? queue.poll() : null;
            if (polled == null) {
                return false;
            }
            Thread thread = Thread.currentThread();
            String name = thread.getName();
            try {
                thread.setName(name + " - " + polled.toString());
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
