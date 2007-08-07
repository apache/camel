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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * A <a href="http://activemq.apache.org/camel/throttler.html">Throttler</a>
 * will set a limit on the maximum number of message exchanges which can be sent
 * to a processor within a specific time period. <p/> This pattern can be
 * extremely useful if you have some external system which meters access; such
 * as only allowing 100 requests per second; or if huge load can cause a
 * particular systme to malfunction or to reduce its throughput you might want
 * to introduce some throttling.
 * 
 * @version $Revision: $
 */
public class Throttler extends DelayProcessorSupport {
    private long maximumRequestsPerPeriod;
    private long timePeriodMillis;
    private long startTimeMillis;
    private long requestCount;

    public Throttler(Processor processor, long maximumRequestsPerPeriod) {
        this(processor, maximumRequestsPerPeriod, 1000);
    }

    public Throttler(Processor processor, long maximumRequestsPerPeriod, long timePeriodMillis) {
        super(processor);
        this.maximumRequestsPerPeriod = maximumRequestsPerPeriod;
        this.timePeriodMillis = timePeriodMillis;
    }

    @Override
    public String toString() {
        return "Throttler[requests: " + maximumRequestsPerPeriod + " per: " + timePeriodMillis + " (ms) to: "
               + getProcessor() + "]";
    }

    // Properties
    // -----------------------------------------------------------------------
    public long getMaximumRequestsPerPeriod() {
        return maximumRequestsPerPeriod;
    }

    /**
     * Sets the maximum number of requests per time period
     */
    public void setMaximumRequestsPerPeriod(long maximumRequestsPerPeriod) {
        this.maximumRequestsPerPeriod = maximumRequestsPerPeriod;
    }

    public long getTimePeriodMillis() {
        return timePeriodMillis;
    }

    /**
     * Sets the time period during which the maximum number of requests apply
     */
    public void setTimePeriodMillis(long timePeriodMillis) {
        this.timePeriodMillis = timePeriodMillis;
    }

    /**
     * The number of requests which have taken place so far within this time
     * period
     */
    public long getRequestCount() {
        return requestCount;
    }

    /**
     * The start time when this current period began
     */
    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    // Implementation methods
    // -----------------------------------------------------------------------
    protected void delay(Exchange exchange) throws Exception {
        long now = currentSystemTime();
        if (startTimeMillis == 0) {
            startTimeMillis = now;
        }
        if (now - startTimeMillis > timePeriodMillis) {
            // we're at the start of a new time period
            // so lets reset things
            requestCount = 1;
            startTimeMillis = now;
        } else {
            if (++requestCount > maximumRequestsPerPeriod) {
                // lets sleep until the start of the next time period
                long time = startTimeMillis + timePeriodMillis;
                waitUntil(time, exchange);
            }
        }
    }
}
