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
package org.apache.camel.test.dst;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * The target of the rewritten time reads of the code under test (see {@link DstInstrumentation}): the virtual clock of
 * the active run, else the real clock.
 * <p>
 * Rewritten: {@code System.currentTimeMillis()}, {@code System.nanoTime()}, {@code new Date()}, {@code Instant.now()},
 * {@code Clock.systemUTC()}, {@code Clock.systemDefaultZone()}, {@code Clock.system(ZoneId)} and {@code now()} /
 * {@code now(ZoneId)} of {@code LocalDateTime}, {@code ZonedDateTime}, {@code OffsetDateTime}, {@code LocalDate},
 * {@code LocalTime} and {@code OffsetTime}. A {@link Clock} returned here reads the virtual time of whichever run is
 * active when it is read, so a clock kept in a field works across runs.
 */
public final class DstClock {

    private static final long NANO_OFFSET = 1_000_000_000_000L;

    private DstClock() {
    }

    private static Dst read() {
        Dst dst = Dst.active();
        if (dst != null && !dst.isLoopThread()) {
            DstEscapes.recordRuntime("virtual clock read from a thread outside the harness: "
                                     + Thread.currentThread().getName());
        }
        return dst;
    }

    public static long currentTimeMillis() {
        Dst dst = read();
        return dst == null ? System.currentTimeMillis() : dst.currentTimeMillis();
    }

    public static long nanoTime() {
        Dst dst = read();
        return dst == null ? System.nanoTime() : NANO_OFFSET + dst.now() * 1_000_000L;
    }

    public static Instant instantNow() {
        Dst dst = read();
        return dst == null ? Instant.now() : Instant.ofEpochMilli(dst.currentTimeMillis());
    }

    // ------------------------------------------------------------------------------------------------------------
    // java.time.Clock

    public static Clock systemUTC() {
        return new VirtualClock(ZoneOffset.UTC);
    }

    public static Clock systemDefaultZone() {
        return new VirtualClock(ZoneId.systemDefault());
    }

    public static Clock system(ZoneId zone) {
        return new VirtualClock(zone);
    }

    // ------------------------------------------------------------------------------------------------------------
    // now() and now(ZoneId) of the java.time types

    public static LocalDateTime localDateTimeNow() {
        return LocalDateTime.now(systemDefaultZone());
    }

    public static LocalDateTime localDateTimeNow(ZoneId zone) {
        return LocalDateTime.now(system(zone));
    }

    public static ZonedDateTime zonedDateTimeNow() {
        return ZonedDateTime.now(systemDefaultZone());
    }

    public static ZonedDateTime zonedDateTimeNow(ZoneId zone) {
        return ZonedDateTime.now(system(zone));
    }

    public static OffsetDateTime offsetDateTimeNow() {
        return OffsetDateTime.now(systemDefaultZone());
    }

    public static OffsetDateTime offsetDateTimeNow(ZoneId zone) {
        return OffsetDateTime.now(system(zone));
    }

    public static LocalDate localDateNow() {
        return LocalDate.now(systemDefaultZone());
    }

    public static LocalDate localDateNow(ZoneId zone) {
        return LocalDate.now(system(zone));
    }

    public static LocalTime localTimeNow() {
        return LocalTime.now(systemDefaultZone());
    }

    public static LocalTime localTimeNow(ZoneId zone) {
        return LocalTime.now(system(zone));
    }

    public static OffsetTime offsetTimeNow() {
        return OffsetTime.now(systemDefaultZone());
    }

    public static OffsetTime offsetTimeNow(ZoneId zone) {
        return OffsetTime.now(system(zone));
    }

    /** A clock that reads the virtual time of the active run, else the real time. */
    private static final class VirtualClock extends Clock {
        private final ZoneId zone;

        VirtualClock(ZoneId zone) {
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new VirtualClock(zone);
        }

        @Override
        public long millis() {
            return currentTimeMillis();
        }

        @Override
        public Instant instant() {
            return instantNow();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof VirtualClock other && zone.equals(other.zone);
        }

        @Override
        public int hashCode() {
            return zone.hashCode() + 1;
        }

        @Override
        public String toString() {
            return "DstClock[" + zone + "]";
        }
    }
}
