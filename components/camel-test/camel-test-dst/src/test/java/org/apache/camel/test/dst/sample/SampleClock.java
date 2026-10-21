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
package org.apache.camel.test.dst.sample;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

/**
 * Code under test that reads the clock and sleeps, for the instrumentation test.
 */
public final class SampleClock {

    private static final Clock UTC = Clock.systemUTC();

    private SampleClock() {
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static void nap() throws InterruptedException {
        Thread.sleep(1);
    }

    /** Every java.time and java.util read of the wall clock, as epoch milliseconds. */
    public static List<Long> javaTimeReads() {
        ZoneId utc = ZoneOffset.UTC;
        return List.of(
                Instant.now().toEpochMilli(),
                new Date().getTime(),
                Clock.systemUTC().millis(),
                Clock.systemDefaultZone().instant().toEpochMilli(),
                Clock.system(utc).millis(),
                UTC.millis(),
                LocalDateTime.now(utc).toInstant(ZoneOffset.UTC).toEpochMilli(),
                LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                ZonedDateTime.now().toInstant().toEpochMilli(),
                ZonedDateTime.now(utc).toInstant().toEpochMilli(),
                OffsetDateTime.now().toInstant().toEpochMilli(),
                OffsetDateTime.now(utc).toInstant().toEpochMilli());
    }

    /** The day of the virtual clock (UTC). */
    public static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
