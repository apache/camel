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
package org.apache.camel.converter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Converter;
import org.apache.camel.util.ObjectHelper;

/**
 * Date and time related converters.
 */
@Converter(generateBulkLoader = true)
public final class DateTimeConverter {

    /**
     * Utility classes should not have a public constructor.
     */
    private DateTimeConverter() {
    }

    @Converter(order = 1)
    public static TimeZone toTimeZone(String s) {
        return TimeZone.getTimeZone(s);
    }

    @Converter(order = 2)
    public static Date toDate(Long l) {
        return new Date(l);
    }

    @Converter(order = 3)
    public static Long toLong(Date date) {
        return date.getTime();
    }

    @Converter(order = 4)
    public static TimeUnit toTimeUnit(String unit) {
        String match = unit.toUpperCase(Locale.ROOT).trim();
        return switch (match) {
            case "DAYS" -> TimeUnit.DAYS;
            case "HOURS" -> TimeUnit.HOURS;
            case "MINUTES" -> TimeUnit.MINUTES;
            case "SECONDS" -> TimeUnit.SECONDS;
            case "MILLISECONDS" -> TimeUnit.MILLISECONDS;
            case "MICROSECONDS" -> TimeUnit.MICROSECONDS;
            case "NANOSECONDS" -> TimeUnit.NANOSECONDS;
            default -> throw new IllegalStateException("Unexpected value: " + unit);
        };
    }

    /**
     * Converts an ISO-8601 date-time with an offset, such as {@code 2026-11-18T23:00:00+01:00} or
     * {@code 2026-11-18T22:00:00Z}, so that an option of this type can be set in an endpoint URI or as a property. An
     * empty value converts to {@code null}.
     * <p/>
     * An ISO-8601 date-time never contains a space, so a space is read as a {@code +}: in an endpoint URI the {@code +}
     * of a positive offset is decoded to a space, unless the value is written as {@code %2B} or in {@code RAW(...)}.
     */
    @Converter(order = 5, allowNull = true)
    public static OffsetDateTime toOffsetDateTime(String value) {
        String text = toIsoText(value);
        return text != null ? OffsetDateTime.parse(text) : null;
    }

    /**
     * Converts an ISO-8601 date-time with an offset and an optional zone id, such as
     * {@code 2026-11-18T23:00:00+01:00[Europe/Paris]} or {@code 2026-11-18T22:00:00Z}, so that an option of this type
     * can be set in an endpoint URI or as a property. An empty value converts to {@code null}.
     * <p/>
     * An ISO-8601 date-time never contains a space, so a space is read as a {@code +}: in an endpoint URI the {@code +}
     * of a positive offset is decoded to a space, unless the value is written as {@code %2B} or in {@code RAW(...)}.
     */
    @Converter(order = 6, allowNull = true)
    public static ZonedDateTime toZonedDateTime(String value) {
        String text = toIsoText(value);
        return text != null ? ZonedDateTime.parse(text) : null;
    }

    /**
     * Converts an ISO-8601 instant, such as {@code 2026-11-18T22:00:00Z} or {@code 2026-11-18T23:00:00+01:00}, so that
     * an option of this type can be set in an endpoint URI or as a property. An empty value converts to {@code null}.
     * <p/>
     * An ISO-8601 date-time never contains a space, so a space is read as a {@code +}: in an endpoint URI the {@code +}
     * of a positive offset is decoded to a space, unless the value is written as {@code %2B} or in {@code RAW(...)}.
     */
    @Converter(order = 7, allowNull = true)
    public static Instant toInstant(String value) {
        String text = toIsoText(value);
        return text != null ? Instant.parse(text) : null;
    }

    private static String toIsoText(String value) {
        if (ObjectHelper.isEmpty(value)) {
            return null;
        }
        return value.trim().replace(' ', '+');
    }
}
