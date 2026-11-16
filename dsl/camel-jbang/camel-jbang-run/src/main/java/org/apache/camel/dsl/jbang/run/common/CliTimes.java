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
package org.apache.camel.dsl.jbang.run.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.TimeZone;

/**
 * Local times for the output of the CLI, without the time zone rules of java.time. {@code ZoneId.systemDefault()}, and
 * parsing a time with a region such as {@code [Europe/Paris]}, load those rules, and the JDK looks for their providers
 * with a {@code ServiceLoader} in every JAR of the class path: about 50 ms with the 340 JARs of the distribution. The
 * offset of the default {@link TimeZone} at a time is the same (both read the tzdb data of the JDK), and needs no
 * lookup.
 */
public final class CliTimes {

    private CliTimes() {
    }

    /**
     * The offset of the time zone of this JVM at the given time.
     */
    public static ZoneOffset offsetAt(long epochMillis) {
        return ZoneOffset.ofTotalSeconds(TimeZone.getDefault().getOffset(epochMillis) / 1000);
    }

    /**
     * The local date and time of the given time, in the time zone of this JVM.
     */
    public static LocalDateTime localDateTime(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), offsetAt(epochMillis));
    }

    /**
     * The given time formatted in the time zone of this JVM.
     */
    public static String format(DateTimeFormatter formatter, long epochMillis) {
        return formatter.withZone(offsetAt(epochMillis)).format(Instant.ofEpochMilli(epochMillis));
    }

    /**
     * The epoch millis of an ISO date-time with an offset, such as the text of a {@link ZonedDateTime}:
     * {@code 2026-11-16T15:38:49.123+01:00[Europe/Copenhagen]}. The region in brackets is left out, as the offset gives
     * the instant.
     *
     * @throws DateTimeParseException when the text is not such a date-time
     */
    public static long parseEpochMillis(String text) {
        int region = text.indexOf('[');
        try {
            return OffsetDateTime.parse(region > 0 ? text.substring(0, region) : text).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            // not with an offset: java.time parses it with the zone rules
            return ZonedDateTime.parse(text).toInstant().toEpochMilli();
        }
    }
}
