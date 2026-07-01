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
package org.apache.camel.management.mbean;

import org.apache.camel.util.StopWatch;

/**
 * Holds the throughput (messages/second) using EWMA (exponentially weighted moving average) smoothing, modeled after
 * Unix load averages (same approach as {@link LoadTriplet}).
 *
 * The instantaneous rate from each 1-second sampling interval is smoothed with a 1-minute decay window so that the
 * reported value converges to the true average rate instead of oscillating between 0 and spike values.
 */
public final class LoadThroughput {

    // EWMA exponent for a 1-minute decay window, sampled every 1 second
    private static final double EXP_1 = Math.exp(-1.0 / 60.0);

    private final StopWatch watch = new StopWatch(false);
    private long last;
    private double thp;

    /**
     * Update the throughput statistics
     *
     * @param currentReading the current cumulative exchange count
     */
    public void update(long currentReading) {
        if (!watch.isStarted()) {
            watch.restart();
            thp = 0;
        } else {
            long time = watch.takenAndRestart();
            if (time > 0) {
                long delta = currentReading - last;
                // instantaneous rate in exchanges/second for this interval
                double instantRate = (1000d / time) * delta;
                // apply EWMA smoothing
                thp = instantRate + EXP_1 * (thp - instantRate);
            }
        }
        last = currentReading;
    }

    public double getThroughput() {
        return thp;
    }

    public void reset() {
        last = 0;
        thp = 0;
    }

    @Override
    public String toString() {
        return Double.toString(thp);
    }

}
