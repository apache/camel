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

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.util.ObjectHelper;

/**
 * A <a href="http://camel.apache.org/throttler.html">Throttler</a>
 * will set a limit on the maximum number of message exchanges which can be sent
 * to a processor within a specific time period. <p/> This pattern can be
 * extremely useful if you have some external system which meters access; such
 * as only allowing 100 requests per second; or if huge load can cause a
 * particular system to malfunction or to reduce its throughput you might want
 * to introduce some throttling.
 * 
 * @version 
 */
public class Throttler extends DelayProcessorSupport implements Traceable {
    private long maximumRequestsPerPeriod;
    private Expression maxRequestsPerPeriodExpression;
    private long timePeriodMillis = 1000;
    private volatile TimeSlot slot;

    public Throttler(CamelContext camelContext, Processor processor, Expression maxRequestsPerPeriodExpression, long timePeriodMillis,
                     ScheduledExecutorService executorService, boolean shutdownExecutorService) {
        super(camelContext, processor, executorService, shutdownExecutorService);

        ObjectHelper.notNull(maxRequestsPerPeriodExpression, "maxRequestsPerPeriodExpression");
        this.maxRequestsPerPeriodExpression = maxRequestsPerPeriodExpression;

        if (timePeriodMillis <= 0) {
            throw new IllegalArgumentException("TimePeriodMillis should be a positive number, was: " + timePeriodMillis);
        }
        this.timePeriodMillis = timePeriodMillis;
    }

    @Override
    public String toString() {
        return "Throttler[requests: " + maxRequestsPerPeriodExpression + " per: " + timePeriodMillis + " (ms) to: "
               + getProcessor() + "]";
    }

    public String getTraceLabel() {
        return "throttle[" + maxRequestsPerPeriodExpression + " per: " + timePeriodMillis + "]";
    }

    // Properties
    // -----------------------------------------------------------------------

    /**
     * Sets the maximum number of requests per time period expression
     */
    public void setMaximumRequestsPerPeriodExpression(Expression maxRequestsPerPeriodExpression) {
        this.maxRequestsPerPeriodExpression = maxRequestsPerPeriodExpression;
    }

    public Expression getMaximumRequestsPerPeriodExpression() {
        return maxRequestsPerPeriodExpression;
    }
    
    public long getTimePeriodMillis() {
        return timePeriodMillis;
    }

    /**
     * Gets the current maximum request per period value.
     */
    public long getCurrentMaximumRequestsPerPeriod() {
        return maximumRequestsPerPeriod;
    }

    /**
     * Sets the time period during which the maximum number of requests apply
     */
    public void setTimePeriodMillis(long timePeriodMillis) {
        this.timePeriodMillis = timePeriodMillis;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    protected long calculateDelay(Exchange exchange) {
        Long longValue = maxRequestsPerPeriodExpression.evaluate(exchange, Long.class);
        if (longValue != null) {
            // log if we changed max period after initial setting
            if (maximumRequestsPerPeriod > 0 && longValue.longValue() != maximumRequestsPerPeriod) {
                log.debug("Throttler changed maximum requests per period from {} to {}", maximumRequestsPerPeriod, longValue);
            }
            maximumRequestsPerPeriod = longValue;
        }

        if (maximumRequestsPerPeriod <= 0) {
            throw new IllegalStateException("The maximumRequestsPerPeriod must be a positive number, was: " + maximumRequestsPerPeriod);
        }

        TimeSlot slot = nextSlot();
        if (!slot.isActive()) {
            long delay = slot.startTime - currentSystemTime();
            return delay;
        } else {
            return 0;
        }
    }
    
    /*
     * Determine what the next available time slot is for handling an Exchange
     */
    protected synchronized TimeSlot nextSlot() {
        if (slot == null) {
            slot = new TimeSlot();
        }
        if (slot.isFull()) {
            slot = slot.next();
        }
        slot.assign();
        return slot;
    }

    /*
    * A time slot is capable of handling a number of exchanges within a certain period of time.
    */
    protected class TimeSlot {
        
        private volatile long capacity = Throttler.this.maximumRequestsPerPeriod;
        private final long duration = Throttler.this.timePeriodMillis;
        private final long startTime;

        protected TimeSlot() {
            this(System.currentTimeMillis());
        }

        protected TimeSlot(long startTime) {
            this.startTime = startTime;
        }

        protected void assign() {
            capacity--;
        }
        
        /*
         * Start the next time slot either now or in the future
         * (no time slots are being created in the past)
         */
        protected TimeSlot next() {
            return new TimeSlot(Math.max(System.currentTimeMillis(), this.startTime + this.duration));
        }
        
        protected boolean isActive() {
            return startTime <= System.currentTimeMillis();
        }
        
        protected boolean isFull() {
            return capacity <= 0;
        }        
    }
}
