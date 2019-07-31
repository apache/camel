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

import org.apache.camel.AsyncCallback;

/**
 * SPI to plugin different reactive engines in the Camel routing engine.
 */
public interface ReactiveExecutor {

    /**
     * Schedules the task to be run
     *
     * @param runnable    the task
     */
    default void schedule(Runnable runnable) {
        schedule(runnable, null);
    }

    /**
     * Schedules the task to be run
     *
     * @param runnable    the task
     * @param description a human readable description for logging purpose
     */
    void schedule(Runnable runnable, String description);

    /**
     * Schedules the task to be prioritized and run asap
     *
     * @param runnable    the task
     */
    default void scheduleMain(Runnable runnable) {
        scheduleMain(runnable, null);
    }

    /**
     * Schedules the task to be prioritized and run asap
     *
     * @param runnable    the task
     * @param description a human readable description for logging purpose
     */
    void scheduleMain(Runnable runnable, String description);

    /**
     * Schedules the task to run synchronously
     *
     * @param runnable    the task
     */
    default void scheduleSync(Runnable runnable) {
        scheduleSync(runnable, null);
    }

    /**
     * Schedules the task to run synchronously
     *
     * @param runnable    the task
     * @param description a human readable description for logging purpose
     */
    void scheduleSync(Runnable runnable, String description);

    /**
     * Executes the next task (if supported by the reactive executor implementation)
     *
     * @return true if a task was executed or false if no more pending tasks
     */
    boolean executeFromQueue();

    /**
     * Schedules the callback to be run
     *
     * @param callback    the callable
     */
    default void callback(AsyncCallback callback) {
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

}
