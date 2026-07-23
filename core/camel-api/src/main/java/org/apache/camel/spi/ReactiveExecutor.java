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
package org.apache.camel.spi;

/**
 * SPI for plugging a custom reactive (non-blocking) dispatch engine into Camel's internal routing loop.
 * <p/>
 * Camel's async routing engine decomposes message processing into a series of continuation tasks rather than blocking a
 * thread for the full duration of a route. This interface is the hook point that decides how those continuations are
 * queued and executed: {@link #schedule(Runnable)} for fair background dispatch, {@link #scheduleMain(Runnable)} for
 * high-priority dispatch that should run as soon as possible, {@link #scheduleSync(Runnable)} to run on the current
 * thread immediately, and {@link #scheduleQueue(Runnable)} to defer to the current-thread queue (used for transactional
 * routing where all steps must run on the same thread).
 * <p/>
 * The built-in {@code DefaultReactiveExecutor} uses a simple single-threaded loop driven by the calling thread.
 * Alternative implementations can delegate to Vert.x, Project Reactor, or other reactive runtimes. The factory key
 * {@link #FACTORY} is used by {@link FactoryFinder} for discovery.
 * <p/>
 * See <a href="https://camel.apache.org/manual/threading-model.html">Threading Model</a> in the Camel user manual.
 *
 * @see   ExecutorServiceManager
 * @since 3.0
 */
public interface ReactiveExecutor {

    /**
     * Service factory key.
     */
    String FACTORY = "reactive-executor";

    /**
     * Schedules the task to be run (fairly)
     *
     * @param runnable the task
     */
    void schedule(Runnable runnable);

    /**
     * Schedules the task to be prioritized and run asap
     *
     * @param runnable the task
     */
    void scheduleMain(Runnable runnable);

    /**
     * Schedules the task to run synchronously (current thread)
     *
     * @param runnable the task
     */
    void scheduleSync(Runnable runnable);

    /**
     * Schedules the task to be run later from the queue (current thread)
     *
     * This is used for routing {@link org.apache.camel.Exchange} using transactions.
     *
     * @param runnable the task
     */
    void scheduleQueue(Runnable runnable);

    /**
     * Executes the next task (if supported by the reactive executor implementation)
     *
     * @return true if a task was executed or false if no more pending tasks
     */
    boolean executeFromQueue();

    /**
     * To enable statistics
     */
    void setStatisticsEnabled(boolean statisticsEnabled);

    /**
     * Whether statistics is enabled
     */
    boolean isStatisticsEnabled();

}
