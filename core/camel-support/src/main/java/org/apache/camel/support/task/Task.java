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

package org.apache.camel.support.task;

import java.time.Duration;

/**
 * A task defines a piece of code that may be executed - in the foreground or background - within a certain budget that
 * is specific to the task type.
 */
public interface Task {

    enum Status {
        Active,
        Inactive,
        Exhausted,
        Completed,
        Failed
    }

    /**
     * Optional name of the task
     */
    String getName();

    /**
     * Gets the task status.
     */
    Status getStatus();

    /**
     * How long it took to run the task when the task was completed
     *
     * @return The duration to execute the task
     */
    Duration elapsed();

    /**
     * The current number of iterations (such as when the task is being repeated)
     *
     * @return the current number of iterations
     */
    int iteration();

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
}
