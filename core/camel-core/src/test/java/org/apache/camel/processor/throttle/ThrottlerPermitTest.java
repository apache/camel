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
package org.apache.camel.processor.throttle;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * When a Throttler has not been used for longer than {@link java.lang.Integer#MAX_VALUE}, casting the result of the
 * {@link ThrottlePermit} comparison to an int causes an overflow. Using a value comparison prevents this issue.
 */
public class ThrottlerPermitTest {

    @Test
    public void testThrottlerPermitWithOldScheduledTime() {
        long timeMillis = System.currentTimeMillis();
        // 30 days in the past
        ThrottlePermit throttlePermitOld = new ThrottlePermit(timeMillis - 2592000000L);
        // Now
        ThrottlePermit throttlePermitNow = new ThrottlePermit(timeMillis);
        ThrottlePermit throttlePermitNow2 = new ThrottlePermit(timeMillis);
        // Future
        ThrottlePermit throttlePermitFuture = new ThrottlePermit(timeMillis + 1000);

        assertEquals(-1, throttlePermitOld.compareTo(throttlePermitNow));
        assertEquals(0, throttlePermitNow.compareTo(throttlePermitNow2));
        assertEquals(1, throttlePermitFuture.compareTo(throttlePermitNow));
    }

    private static final class ThrottlePermit implements Delayed {
        private volatile long scheduledTime;

        ThrottlePermit(final long delayMs) {
            setDelayMs(delayMs);
        }

        public void setDelayMs(final long delayMs) {
            this.scheduledTime = System.currentTimeMillis() + delayMs;
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return unit.convert(scheduledTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(final Delayed o) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}
