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
package org.apache.camel.component.google.calendar;

import java.util.HashMap;
import java.util.Map;

import com.google.api.services.calendar.model.Calendar;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.calendar.internal.CalendarCalendarsApiMethod;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.calendar.Calendar$Calendars} APIs.
 */
public class CalendarCalendarsIntegrationTest extends AbstractGoogleCalendarTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarCalendarsIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleCalendarApiCollection.getCollection().getApiName(CalendarCalendarsApiMethod.class).getName();

    @Test
    public void testCalendars() throws Exception {
        Calendar calendar = getCalendar();
        Calendar calendarFromGet = requestBody("direct://GET", calendar.getId());
        assertTrue(calendar.getId().equals(calendarFromGet.getId()));

        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelGoogleCalendar.calendarId", calendar.getId());
        // parameter type is com.google.api.services.calendar.model.Calendar
        headers.put("CamelGoogleCalendar.content", calendar.setDescription("foo"));

        Calendar result = requestBodyAndHeaders("direct://UPDATE", null, headers);
        assertTrue("foo".equals(result.getDescription()));

        requestBody("direct://DELETE", calendar.getId());
        try {
            calendarFromGet = requestBody("direct://GET", calendar.getId());
            assertTrue("Should have not found deleted calendar.", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for clear
                from("direct://CLEAR").to("google-calendar://" + PATH_PREFIX + "/clear?inBody=calendarId");

                // test route for delete
                from("direct://DELETE").to("google-calendar://" + PATH_PREFIX + "/delete?inBody=calendarId");

                // test route for get
                from("direct://GET").to("google-calendar://" + PATH_PREFIX + "/get?inBody=calendarId");

                // test route for insert
                from("direct://INSERT").to("google-calendar://" + PATH_PREFIX + "/insert?inBody=content");

                // test route for patch
                from("direct://PATCH").to("google-calendar://" + PATH_PREFIX + "/patch");

                // test route for update
                from("direct://UPDATE").to("google-calendar://" + PATH_PREFIX + "/update");

            }
        };
    }
}
