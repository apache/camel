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
package org.apache.camel.itest.springboot.arquillian.container.util;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class Await {

    private static final long DEFAULT_SLEEP_INTERVAL = 100L;

    private final long delay;

    private final long sleepInterval;

    private final Callable<Boolean> condition;

    /**
     *
     * @param delay
     * @param condition
     */
    public Await(long delay, Callable<Boolean> condition) {
        this(DEFAULT_SLEEP_INTERVAL, delay, condition);
    }

    /**
     *
     * @param delay The delay in seconds
     * @param sleepInterval Thread sleep interval in ms
     * @param condition
     */
    public Await(long sleepInterval, long delay, Callable<Boolean> condition) {
        this.delay = TimeUnit.SECONDS.toMillis(delay);
        this.sleepInterval = sleepInterval;
        this.condition = condition;
    }

    public boolean start() {
        long start = System.currentTimeMillis();
        do {
            try {
                if (condition.call()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(sleepInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (!isTimeoutExpired(start));
        return false;
    }

    private boolean isTimeoutExpired(long start) {
        return (System.currentTimeMillis() - start) >= delay;
    }

}
