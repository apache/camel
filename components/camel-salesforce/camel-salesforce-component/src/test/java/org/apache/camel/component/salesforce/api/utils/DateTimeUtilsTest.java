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
package org.apache.camel.component.salesforce.api.utils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DateTimeUtilsTest {

    @Test
    public void testFormatDateTime() {
        assertEquals("1991-12-10T12:13:14.007+01:00", DateTimeUtils.formatDateTime(ZonedDateTime.of(1991, 12, 10, 12, 13, 14, 7000000, ZoneId.of("UTC+01:00:21"))));
        assertEquals("1991-12-10T12:13:14.007Z", DateTimeUtils.formatDateTime(ZonedDateTime.of(1991, 12, 10, 12, 13, 14, 7000000, ZoneId.of("UTC"))));
        assertEquals("1700-01-01T01:13:14.007+00:19", DateTimeUtils.formatDateTime(ZonedDateTime.of(1700, 1, 1, 1, 13, 14, 7000000, ZoneId.of("UTC+00:19:21"))));
        assertEquals("1700-02-03T02:13:14.007Z", DateTimeUtils.formatDateTime(ZonedDateTime.of(1700, 2, 3, 2, 13, 14, 7000000, ZoneId.of("UTC"))));
    }

    @Test
    public void testParseDateTime() {
        assertEquals(ZonedDateTime.of(1991, 12, 10, 12, 13, 14, 7000000, ZoneId.of("+01:00")), DateTimeUtils.parseDateTime("1991-12-10T12:13:14.007+01:00"));
        assertEquals(ZonedDateTime.of(1991, 12, 10, 12, 13, 14, 0, ZoneId.of("Z")), DateTimeUtils.parseDateTime("1991-12-10T12:13:14+00:00"));
        assertEquals(ZonedDateTime.of(1991, 12, 10, 12, 13, 14, 0, ZoneId.of("Z")), DateTimeUtils.parseDateTime("1991-12-10T12:13:14.000+00:00"));
        assertEquals(ZonedDateTime.of(1991, 12, 10, 12, 13, 14, 0, ZoneId.of("Z")), DateTimeUtils.parseDateTime("1991-12-10T12:13:14+0000"));
        assertEquals(ZonedDateTime.of(1991, 12, 10, 12, 13, 14, 0, ZoneId.of("Z")), DateTimeUtils.parseDateTime("1991-12-10T12:13:14.000+0000"));
        assertEquals(ZonedDateTime.of(1991, 12, 10, 12, 13, 14, 7000000, ZoneId.of("Z")), DateTimeUtils.parseDateTime("1991-12-10T12:13:14.007Z"));
        assertEquals(ZonedDateTime.of(1700, 1, 1, 1, 13, 14, 7000000, ZoneId.of("+00:19")), DateTimeUtils.parseDateTime("1700-01-01T01:13:14.007+00:19"));
        assertEquals(ZonedDateTime.of(1700, 2, 3, 2, 13, 14, 7000000, ZoneId.of("Z")), DateTimeUtils.parseDateTime("1700-02-03T02:13:14.007Z"));
    }

    @Test
    public void testFormatDate() {
        assertEquals("1991-12-10", DateTimeUtils.formatDate(LocalDate.of(1991, 12, 10)));
        assertEquals("2100-12-10", DateTimeUtils.formatDate(LocalDate.of(2100, 12, 10)));
        assertEquals("1700-01-01", DateTimeUtils.formatDate(LocalDate.of(1700,  1,  1)));
        }

    @Test
    public void testParseDate() {
        assertEquals(LocalDate.of(1700, 01, 01), DateTimeUtils.parseDate("1700-01-01"));
        assertEquals(LocalDate.of(2100, 12, 10), DateTimeUtils.parseDate("2100-12-10"));
        assertEquals(LocalDate.of(1700,  1,  1), DateTimeUtils.parseDate("1700-01-01"));
    }

    @Test
    public void testFormatTime() {
        assertEquals("12:13:14.007", DateTimeUtils.formatTime(LocalTime.of(12, 13, 14, 7000000)));
        assertEquals("01:00:00", DateTimeUtils.formatTime(LocalTime.of(1, 0, 0, 0)));
        assertEquals("00:00:00", DateTimeUtils.formatTime(LocalTime.of(0, 0, 0)));
    }

    @Test
    public void testParseTime() {
        assertEquals(LocalTime.of(0,0,0), DateTimeUtils.parseTime("00:00:00.000"));
        assertEquals(LocalTime.of(1,0,0,100), DateTimeUtils.parseTime("01:00:00.0000001"));
        assertEquals(LocalTime.of(12,13,14,7000000), DateTimeUtils.parseTime("12:13:14.007"));
        assertEquals(LocalTime.of(12,13,0), DateTimeUtils.parseTime("12:13"));
    }
}
