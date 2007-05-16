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
package org.apache.camel.bam;

import java.util.concurrent.TimeUnit;
import java.util.Date;

/**
 * A fluent builder of times
 *
 * @version $Revision: $
 */
public class TimeBuilder {
    private long number;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private boolean configuredTime;

    /**
     * Creates a time which by default is in milliseconds unless
     * specified using a time based builder method
     */
    public static TimeBuilder time(long number) {
        return new TimeBuilder(number);
    }

    public TimeBuilder(long number) {
        this.number = number;
    }

    public long toMillis() {
        return timeUnit.toMillis(number);
    }
    
    public Date toDate() {
        return new Date(toMillis());
    }

    public TimeBuilder millis() {
        setTimeUnit(TimeUnit.MILLISECONDS);
        return this;
    }

    public TimeBuilder nanos() {
        setTimeUnit(TimeUnit.NANOSECONDS);
        return this;
    }

    public TimeBuilder micros() {
        setTimeUnit(TimeUnit.MICROSECONDS);
        return this;
    }

    public TimeBuilder seconds() {
        setTimeUnit(TimeUnit.SECONDS);
        return this;
    }

    public TimeBuilder minutes() {
        setTimeUnit(TimeUnit.SECONDS);
        number = minutesAsSeconds(number);
        return this;
    }

    public TimeBuilder hours() {
        setTimeUnit(TimeUnit.SECONDS);
        number = hoursAsSeconds(number);
        return this;
    }

    public TimeBuilder days() {
        setTimeUnit(TimeUnit.SECONDS);
        number = daysAsSeconds(number);
        return this;
    }

    public long getNumber() {
        return number;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        if (configuredTime) {
            throw new IllegalArgumentException("Cannot configure the time unit twice!");
        }
        else {
           configuredTime = true;
        }
        this.timeUnit = timeUnit;
    }

    protected long minutesAsSeconds(long value) {
        return value * 60;
    }

    protected long hoursAsSeconds(long value) {
        return minutesAsSeconds(value) * 60;
    }

    protected long daysAsSeconds(long value) {
        return hoursAsSeconds(value) * 24;
    }

}
