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
 * SPI to plugin different reactive engines in the Camel routing engine.
 */
public interface ReactiveExecutor {

    /**
     * Service factory key.
     */
    String FACTORY = "reactive-executor";

    /**
     * Schedules the task to be run
     *
     * @param runnable    the task
     */
    void schedule(Runnable runnable);

    /**
     * Schedules the task to be prioritized and run asap
     *
     * @param runnable    the task
     */
    void scheduleMain(Runnable runnable);

    /**
     * Schedules the task to run synchronously
     *
     * @param runnable    the task
     */
    void scheduleSync(Runnable runnable);

    /**
     * Executes the next task (if supported by the reactive executor implementation)
     *
     * @return true if a task was executed or false if no more pending tasks
     */
    boolean executeFromQueue();

}
