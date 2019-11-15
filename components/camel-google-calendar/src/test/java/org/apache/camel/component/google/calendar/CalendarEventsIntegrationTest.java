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
package org.apache.camel.component.google.calendar;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.calendar.internal.CalendarEventsApiMethod;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.calendar.Calendar$Events} APIs.
 */
public class CalendarEventsIntegrationTest extends AbstractGoogleCalendarTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarEventsIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleCalendarApiCollection.getCollection().getApiName(CalendarEventsApiMethod.class).getName();

    @Test
    public void testInsert() throws Exception {
        Event event = new Event();

        event.setSummary("Feed the Camel");
        event.setLocation("Somewhere");

        ArrayList<EventAttendee> attendees = new ArrayList<>();
        attendees.add(new EventAttendee().setEmail("camel-google-calendar.janstey@gmail.com"));
        event.setAttendees(attendees);

        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() + 3600000);
        DateTime start = new DateTime(startDate, TimeZone.getTimeZone("UTC"));
        event.setStart(new EventDateTime().setDateTime(start));
        DateTime end = new DateTime(endDate, TimeZone.getTimeZone("UTC"));
        event.setEnd(new EventDateTime().setDateTime(end));

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleCalendar.calendarId", getCalendar().getId());
        // parameter type is com.google.api.services.calendar.model.Event
        headers.put("CamelGoogleCalendar.content", event);

        final com.google.api.services.calendar.model.Event result = requestBodyAndHeaders("direct://INSERT", null, headers);

        assertEquals("Feed the Camel", result.getSummary());
        LOG.debug("insert: " + result);
    }

    @Test
    public void testManipulatingAnEvent() throws Exception {
        // Add an event
        Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleCalendar.calendarId", getCalendar().getId());
        // parameter type is String
        headers.put("CamelGoogleCalendar.text", "Feed the Camel");
        com.google.api.services.calendar.model.Event result = requestBodyAndHeaders("direct://QUICKADD", null, headers);
        assertNotNull("quickAdd result", result);

        // Check if it is in the list of events for this calendar
        com.google.api.services.calendar.model.Events events = requestBody("direct://LIST", getCalendar().getId());
        Event item = events.getItems().get(0);
        String eventId = item.getId();
        assertEquals("Feed the Camel", item.getSummary());

        // Get the event metadata
        headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleCalendar.calendarId", getCalendar().getId());
        // parameter type is String
        headers.put("CamelGoogleCalendar.eventId", eventId);
        result = requestBodyAndHeaders("direct://GET", null, headers);
        assertEquals("Feed the Camel", result.getSummary());

        // Change the event
        result.setSummary("Feed the Camel later");
        // parameter type is com.google.api.services.calendar.model.Event
        headers.put("CamelGoogleCalendar.content", result);
        Event newResult = requestBodyAndHeaders("direct://UPDATE", null, headers);
        assertEquals("Feed the Camel later", newResult.getSummary());

        // Delete the event
        headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelGoogleCalendar.calendarId", getCalendar().getId());
        // parameter type is String
        headers.put("CamelGoogleCalendar.eventId", eventId);
        result = requestBodyAndHeaders("direct://DELETE", null, headers);

        // Check if it is NOT in the list of events for this calendar
        events = requestBody("direct://LIST", getCalendar().getId());
        assertEquals(0, events.getItems().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for calendarImport
                from("direct://CALENDARIMPORT").to("google-calendar://" + PATH_PREFIX + "/calendarImport");

                // test route for delete
                from("direct://DELETE").to("google-calendar://" + PATH_PREFIX + "/delete");

                // test route for get
                from("direct://GET").to("google-calendar://" + PATH_PREFIX + "/get");

                // test route for insert
                from("direct://INSERT").to("google-calendar://" + PATH_PREFIX + "/insert");

                // test route for instances
                from("direct://INSTANCES").to("google-calendar://" + PATH_PREFIX + "/instances");

                // test route for list
                from("direct://LIST").to("google-calendar://" + PATH_PREFIX + "/list?inBody=calendarId");

                // test route for move
                from("direct://MOVE").to("google-calendar://" + PATH_PREFIX + "/move");

                // test route for patch
                from("direct://PATCH").to("google-calendar://" + PATH_PREFIX + "/patch");

                // test route for quickAdd
                from("direct://QUICKADD").to("google-calendar://" + PATH_PREFIX + "/quickAdd");

                // test route for update
                from("direct://UPDATE").to("google-calendar://" + PATH_PREFIX + "/update");

                // test route for watch
                from("direct://WATCH").to("google-calendar://" + PATH_PREFIX + "/watch");

            }
        };
    }
}
