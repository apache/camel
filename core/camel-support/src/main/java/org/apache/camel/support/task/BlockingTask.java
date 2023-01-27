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

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Defines a task that blocks the code execution when ran. The task under execution must be thread-safe.
 */
public interface BlockingTask extends Task {
    /**
     * Run the task
     *
     * @param  predicate the task as a predicate. The result of the predicate is used to check if the task has completed
     *                   or not. The predicate must return true if the execution has completed or false otherwise.
     *                   Failures on the task should be handled on the predicate using the payload as wrapper for In/Out
     *                   if necessary
     * @param  payload   a payload to be passed to the task
     * @param  <T>       The type of the payload passed to the predicate when testing the task
     * @return           true if the task has completed successfully or false if: 1) the budget is exhausted or 2) the
     *                   task was interrupted.
     */
    default <T> boolean run(Predicate<T> predicate, T payload) {
        return this.run(() -> predicate.test(payload));
    }

    /**
     * Run the task
     *
     * @param  supplier the task as a boolean supplier. The result is used to check if the task has completed or not.
     *                  The supplier must return true if the execution has completed or false otherwise.
     * @return          true if the task has completed successfully or false if: 1) the budget is exhausted or 2) the
     *                  task was interrupted.
     */
    boolean run(BooleanSupplier supplier);
}
