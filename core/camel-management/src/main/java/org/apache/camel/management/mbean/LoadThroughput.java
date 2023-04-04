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
 * Holds the load throughput messages/second
 */
public final class LoadThroughput {

    private final StopWatch watch = new StopWatch(false);
    private long last;
    private double thp;

    /**
     * Update the load statistics
     *
     * @param currentReading the current reading
     */
    public void update(long currentReading) {
        if (!watch.isStarted()) {
            watch.restart();
            thp = 0;
        } else {
            long time = watch.takenAndRestart();
            if (time > 0) {
                long delta = currentReading - last;
                if (delta > 0) {
                    // need to calculate with fractions
                    thp = (1000d / time) * delta;
                } else {
                    thp = 0;
                }
            } else {
                thp = 0;
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
