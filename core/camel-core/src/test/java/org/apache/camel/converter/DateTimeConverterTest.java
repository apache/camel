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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.TypeConversionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 */
public class DateTimeConverterTest extends ContextTestSupport {

    @Test
    public void testToTimeZone() {
        String id = TimeZone.getDefault().getID();

        TimeZone zone = context.getTypeConverter().convertTo(TimeZone.class, id);
        assertNotNull(zone);
        assertEquals(id, zone.getID());
    }

    @Test
    public void testLongToDate() {
        long value = 0;
        Date date = context.getTypeConverter().convertTo(Date.class, value);
        Date expected = new Date(value);
        assertEquals(expected, date);
    }

    @Test
    public void testDateToLong() {
        Date date = new Date(0);
        long l = context.getTypeConverter().convertTo(Long.class, date);
        assertEquals(date.getTime(), l);
    }

    @Test
    public void testToTimeUnit() {
        assertEquals(TimeUnit.DAYS, context.getTypeConverter().convertTo(TimeUnit.class, "DAYS"));
        assertEquals(TimeUnit.MILLISECONDS, context.getTypeConverter().convertTo(TimeUnit.class, "MILLISECONDS"));
        assertEquals(TimeUnit.MICROSECONDS, context.getTypeConverter().convertTo(TimeUnit.class, "MICROSECONDS"));
    }

    @Test
    public void testToOffsetDateTime() {
        assertEquals(OffsetDateTime.of(2026, 11, 18, 23, 0, 0, 0, ZoneOffset.ofHours(1)),
                context.getTypeConverter().convertTo(OffsetDateTime.class, "2026-11-18T23:00:00+01:00"));
        assertEquals(OffsetDateTime.of(2026, 11, 18, 22, 0, 0, 0, ZoneOffset.UTC),
                context.getTypeConverter().convertTo(OffsetDateTime.class, " 2026-11-18T22:00:00Z "));
        assertEquals(OffsetDateTime.of(2026, 11, 18, 17, 0, 0, 0, ZoneOffset.ofHours(-5)),
                context.getTypeConverter().convertTo(OffsetDateTime.class, "2026-11-18T17:00:00-05:00"));
    }

    @Test
    public void testToOffsetDateTimeSpaceIsPlus() {
        // the + of a positive offset is decoded to a space in an endpoint uri
        assertEquals(OffsetDateTime.of(2026, 11, 18, 23, 0, 0, 0, ZoneOffset.ofHours(1)),
                context.getTypeConverter().convertTo(OffsetDateTime.class, "2026-11-18T23:00:00 01:00"));
    }

    @Test
    public void testToZonedDateTime() {
        assertEquals(ZonedDateTime.of(2026, 11, 18, 23, 0, 0, 0, ZoneOffset.ofHours(1)),
                context.getTypeConverter().convertTo(ZonedDateTime.class, "2026-11-18T23:00:00+01:00"));
        assertEquals(ZonedDateTime.of(2026, 11, 18, 22, 0, 0, 0, ZoneOffset.UTC),
                context.getTypeConverter().convertTo(ZonedDateTime.class, "2026-11-18T22:00:00Z"));
        assertEquals(ZonedDateTime.of(2026, 11, 18, 23, 0, 0, 0, ZoneId.of("Europe/Paris")),
                context.getTypeConverter().convertTo(ZonedDateTime.class, "2026-11-18T23:00:00+01:00[Europe/Paris]"));
        assertEquals(ZonedDateTime.of(2026, 11, 18, 23, 0, 0, 0, ZoneId.of("Europe/Paris")),
                context.getTypeConverter().convertTo(ZonedDateTime.class, "2026-11-18T23:00:00 01:00[Europe/Paris]"));
    }

    @Test
    public void testToInstant() {
        Instant expected = Instant.parse("2026-11-18T22:00:00Z");
        assertEquals(expected, context.getTypeConverter().convertTo(Instant.class, "2026-11-18T22:00:00Z"));
        assertEquals(expected, context.getTypeConverter().convertTo(Instant.class, "2026-11-18T23:00:00+01:00"));
        assertEquals(expected, context.getTypeConverter().convertTo(Instant.class, "2026-11-18T23:00:00 01:00"));
    }

    @Test
    public void testJavaTimeEmptyIsNull() {
        assertNull(context.getTypeConverter().convertTo(OffsetDateTime.class, ""));
        assertNull(context.getTypeConverter().convertTo(ZonedDateTime.class, " "));
        assertNull(context.getTypeConverter().convertTo(Instant.class, ""));
    }

    @Test
    public void testJavaTimeInvalid() {
        assertThrows(TypeConversionException.class,
                () -> context.getTypeConverter().convertTo(OffsetDateTime.class, "2026-11-18"));
        assertThrows(TypeConversionException.class,
                () -> context.getTypeConverter().convertTo(ZonedDateTime.class, "tomorrow"));
        // an instant needs an offset
        assertThrows(TypeConversionException.class,
                () -> context.getTypeConverter().convertTo(Instant.class, "2026-11-18T23:00:00"));
    }
}
