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
package org.apache.camel.component.ical;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VEvent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Two calendars are unmarshalled one after the other by the same data format. The time zone that the first calendar
 * defines must not be used for the second calendar, which names it without defining it.
 */
class ICalDataFormatTimeZoneTest extends CamelTestSupport {

    private static final String HEAD = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Events Calendar//iCal4j 1.0//EN\r\n";
    private static final String CUSTOM_ZONE = "BEGIN:VTIMEZONE\r\nTZID:Custom/A\r\nBEGIN:STANDARD\r\n"
                                              + "DTSTART:19700101T000000\r\nTZOFFSETFROM:+0300\r\nTZOFFSETTO:+0300\r\n"
                                              + "TZNAME:A\r\nEND:STANDARD\r\nEND:VTIMEZONE\r\n";
    private static final String EVENT = "BEGIN:VEVENT\r\nUID:e1\r\nDTSTAMP:20260101T000000Z\r\nSUMMARY:e1\r\n"
                                        + "DTSTART;TZID=Custom/A:20260101T120000\r\nEND:VEVENT\r\n";
    private static final String END = "END:VCALENDAR\r\n";

    @Test
    void timeZoneOfAnEarlierCalendarIsNotUsed() {
        Temporal defined = startOfTheEvent(HEAD + CUSTOM_ZONE + EVENT + END);
        Temporal notDefined = startOfTheEvent(HEAD + EVENT + END);

        assertEquals(ZoneOffset.ofHours(3), assertInstanceOf(ZonedDateTime.class, defined).getOffset(),
                "the calendar that defines the time zone");
        // ical4j reads a date with a time zone it does not know as a local date-time without a zone
        assertEquals(LocalDateTime.of(2026, 1, 1, 12, 0), notDefined,
                "the calendar that does not define the time zone");
    }

    private Temporal startOfTheEvent(String calendarText) {
        Calendar calendar = template.requestBody("direct:unmarshal", calendarText.getBytes(StandardCharsets.UTF_8),
                Calendar.class);
        VEvent event = (VEvent) calendar.getComponents(Component.VEVENT).get(0);
        return event.getDateTimeStart().getDate();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal").unmarshal().ical(false);
            }
        };
    }
}
