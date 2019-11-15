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

import com.google.api.services.calendar.model.Calendar;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.calendar.internal.CalendarCalendarListApiMethod;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.junit.Test;

/**
 * Test class for {@link com.google.api.services.calendar.Calendar$CalendarList} APIs.
 */
public class CalendarCalendarListIntegrationTest extends AbstractGoogleCalendarTestSupport {

    private static final String PATH_PREFIX = GoogleCalendarApiCollection.getCollection().getApiName(CalendarCalendarListApiMethod.class).getName();

    @Test
    public void testCalendarList() throws Exception {
        Calendar calendar = getCalendar();
        assertTrue("Test calendar should be in the list", isCalendarInList(calendar));

        CalendarListEntry calendarFromGet = requestBody("direct://GET", calendar.getId());
        assertTrue(calendar.getId().equals(calendarFromGet.getId()));
    }

    protected boolean isCalendarInList(Calendar calendar) {
        CalendarList calendarList = requestBody("direct://LIST", null);

        java.util.List<CalendarListEntry> items = calendarList.getItems();

        boolean found = false;
        for (CalendarListEntry calendarListEntry : items) {
            if (calendar.getSummary().equals(calendarListEntry.getSummary())) {
                found = true;
            }
        }
        return found;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for delete
                from("direct://DELETE").to("google-calendar://" + PATH_PREFIX + "/delete?inBody=calendarId");

                // test route for get
                from("direct://GET").to("google-calendar://" + PATH_PREFIX + "/get?inBody=calendarId");

                // test route for insert
                from("direct://INSERT").to("google-calendar://" + PATH_PREFIX + "/insert?inBody=content");

                // test route for list
                from("direct://LIST").to("google-calendar://" + PATH_PREFIX + "/list");

                // test route for patch
                from("direct://PATCH").to("google-calendar://" + PATH_PREFIX + "/patch");

                // test route for update
                from("direct://UPDATE").to("google-calendar://" + PATH_PREFIX + "/update");

                // test route for watch
                from("direct://WATCH").to("google-calendar://" + PATH_PREFIX + "/watch?inBody=contentChannel");

            }
        };
    }
}
