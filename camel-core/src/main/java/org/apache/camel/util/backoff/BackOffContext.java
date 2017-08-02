/**
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


public final class BackOffContext {
    private final BackOff backOff;

    private long currentAttempts;
    private long currentDelay;
    private long currentElapsedTime;

    public BackOffContext(BackOff backOff) {
        this.backOff = backOff;
        this.currentAttempts = 0;
        this.currentDelay = backOff.getDelay().toMillis();
        this.currentElapsedTime = 0;
    }

    // *************************************
    // Properties
    // *************************************

    public BackOff backOff() {
        return backOff;
    }

    public long getCurrentAttempts() {
        return currentAttempts;
    }

    public long getCurrentDelay() {
        return currentDelay;
    }

    public long getCurrentElapsedTime() {
        return currentElapsedTime;
    }

    public boolean isExhausted() {
        return currentDelay == BackOff.NEVER;
    }

    // *************************************
    // Impl
    // *************************************

    public long next() {
        // A call to next when currentDelay is set to NEVER has no effects
        // as this means that either the timer is exhausted or it has explicit
        // stopped
        if (currentDelay != BackOff.NEVER) {

            currentAttempts++;

            if (currentAttempts > backOff.getMaxAttempts()) {
                currentDelay = BackOff.NEVER;
            } else if (currentElapsedTime > backOff.getMaxElapsedTime().toMillis()) {
                currentDelay = BackOff.NEVER;
            } else {
                if (currentDelay <= backOff.getMaxDelay().toMillis()) {
                    currentDelay = (long) (currentDelay * backOff().getMultiplier());
                }

                currentElapsedTime += currentDelay;
            }
        }

        return currentDelay;
    }

    public BackOffContext reset() {
        this.currentAttempts = 0;
        this.currentDelay = 0;
        this.currentElapsedTime = 0;

        return this;
    }

    public BackOffContext stop() {
        this.currentAttempts = 0;
        this.currentDelay = BackOff.NEVER;
        this.currentElapsedTime = 0;

        return this;
    }
}
