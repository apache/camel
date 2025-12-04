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

package org.apache.camel.util.backoff;

import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.camel.util.function.ThrowingFunction;

/**
 * A simple timer utility that use a linked {@link BackOff} to determine when a task should be executed.
 */
public interface BackOffTimer {

    /**
     * Schedules a task to run according to the backoff settings
     *
     * @param  backOff  the settings for how often to run the task
     * @param  function the function to call for each run
     * @return          the task
     */
    Task schedule(BackOff backOff, ThrowingFunction<Task, Boolean, Exception> function);

    /**
     * Gets the name of this timer.
     */
    String getName();

    /**
     * Removes the task
     */
    void remove(Task task);

    /**
     * Access to unmodifiable set of all the tasks
     */
    Set<Task> getTasks();

    /**
     * Number of tasks
     */
    int size();

    // ****************************************
    // TimerTask
    // ****************************************

    interface Task {
        enum Status {
            Active,
            Inactive,
            Exhausted,
            Completed,
            Failed
        }

        /**
         * Name of this task
         */
        String getName();

        /**
         * The back-off associated with this task.
         */
        BackOff getBackOff();

        /**
         * Gets the task status.
         */
        Status getStatus();

        /**
         * The number of attempts so far.
         */
        long getCurrentAttempts();

        /**
         * The current computed delay.
         */
        long getCurrentDelay();

        /**
         * The current elapsed time.
         */
        long getCurrentElapsedTime();

        /**
         * The time the first attempt was performed.
         */
        long getFirstAttemptTime();

        /**
         * The time the last attempt has been performed.
         */
        long getLastAttemptTime();

        /**
         * An indication about the time the next attempt will be made.
         */
        long getNextAttemptTime();

        /**
         * The task failed for some un-expected exception
         */
        Throwable getException();

        /**
         * Reset the task.
         */
        void reset();

        /**
         * Cancel the task.
         */
        void cancel();

        /**
         * Action to execute when the context is completed (cancelled or exhausted)
         *
         * @param whenCompleted the consumer.
         */
        void whenComplete(BiConsumer<Task, Throwable> whenCompleted);
    }
}
