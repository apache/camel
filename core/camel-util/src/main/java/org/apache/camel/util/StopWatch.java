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
package org.apache.camel.util;

import java.time.Duration;

/**
 * A very simple stop watch.
 * <p/>
 * This implementation is not thread safe and can only time one task at any given time.
 */
public final class StopWatch {

    private long start;

    /**
     * Starts the stop watch
     */
    public StopWatch() {
        this.start = System.nanoTime();
    }

    /**
     * Creates the stop watch
     *
     * @param start whether it should start immediately
     */
    public StopWatch(boolean start) {
        if (start) {
            this.start = System.nanoTime();
        }
    }

    /**
     * Starts or restarts the stop watch
     */
    public void restart() {
        start = System.nanoTime();
    }

    /**
     * Whether the watch is started
     */
    public boolean isStarted() {
        return start > 0;
    }

    /**
     * Returns the time taken in millis.
     *
     * @return time in millis, or <tt>0</tt> if not started yet.
     */
    public long taken() {
        if (start > 0) {
            long delta = System.nanoTime() - start;
            return Duration.ofNanos(delta).toMillis();
        }
        return 0;
    }

    /**
     * Returns the time taken in millis and restarts the timer.
     *
     * @return time in millis, or <tt>0</tt> if not started yet.
     */
    public long takenAndRestart() {
        long answer = taken();
        start = System.nanoTime();
        return answer;
    }

    /**
     * Stops the stop watch
     */
    public void stop() {
        start = 0;
    }

}
