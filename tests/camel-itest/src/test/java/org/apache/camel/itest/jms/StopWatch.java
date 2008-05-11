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
package org.apache.camel.itest.jms;

import java.text.NumberFormat;

/**
 * @version $Revision$
 */
public class StopWatch {
    private final String id;
    private int loopCount;
    private int totalLoops;
    private long groupElapsed;
    private long totalElapsed;
    private long startTime;
    private long minTime = Long.MAX_VALUE;
    private long maxTime = Long.MIN_VALUE;
    private int logFrequency = 1000;
    private NumberFormat numberFormat = NumberFormat.getNumberInstance();

    public StopWatch(String id) {
        this.id = id;
    }

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public void stop() {
        long elapsedTime = System.currentTimeMillis() - startTime;
        groupElapsed += elapsedTime;
        totalElapsed += elapsedTime;
        loopCount++;
        totalLoops++;

        if (elapsedTime > maxTime) {
            maxTime = elapsedTime;
        }
        if (elapsedTime < minTime) {
            minTime = elapsedTime;
        }
        if (logFrequency > 0 && loopCount % logFrequency == 0) {
            System.out.println(toString());
            reset();
        }
    }

    protected void reset() {
        loopCount = 0;
        groupElapsed = 0;
        minTime = Long.MAX_VALUE;
        maxTime = Long.MIN_VALUE;
    }

    @Override
    public String toString() {
        double average = totalElapsed;
        average /= totalLoops;
        average /= 1000;
        return id + " count: " + loopCount + " elapsed: " + groupElapsed + " min: " + minTime + " max: " + maxTime + " average: " + formatSeconds(average);
    }

    public int getLogFrequency() {
        return logFrequency;
    }

    public void setLogFrequency(int logFrequency) {
        this.logFrequency = logFrequency;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public long getMinTime() {
        return minTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getGroupElapsed() {
        return groupElapsed;
    }

    protected String formatSeconds(double time) {
        return numberFormat.format(time);
    }
}
