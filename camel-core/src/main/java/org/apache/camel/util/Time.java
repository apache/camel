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
package org.apache.camel.util;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * A helper class for working with times in various units
 * 
 * @version 
 */
public class Time {
    private long number;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    public Time(long number, TimeUnit timeUnit) {
        this.number = number;
        this.timeUnit = timeUnit;
    }

    public static Time millis(long value) {
        return new Time(value, TimeUnit.MILLISECONDS);
    }

    public static Time seconds(long value) {
        return new Time(value, TimeUnit.SECONDS);
    }

    public static Time minutes(long value) {
        return new Time(minutesAsSeconds(value), TimeUnit.SECONDS);
    }

    public static Time hours(long value) {
        return new Time(hoursAsSeconds(value), TimeUnit.SECONDS);
    }

    public static Time days(long value) {
        return new Time(daysAsSeconds(value), TimeUnit.SECONDS);
    }

    public long toMillis() {
        return timeUnit.toMillis(number);
    }

    public Date toDate() {
        return new Date(toMillis());
    }

    public long getNumber() {
        return number;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    protected static long minutesAsSeconds(long value) {
        return value * 60;
    }

    protected static long hoursAsSeconds(long value) {
        return minutesAsSeconds(value) * 60;
    }

    protected static long daysAsSeconds(long value) {
        return hoursAsSeconds(value) * 24;
    }

    @Override
    public String toString() {
        return number + " " + timeUnit.toString().toLowerCase(Locale.ENGLISH);
    }
}
