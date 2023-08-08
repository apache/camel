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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.calendar.internal.CalendarAclApiMethod;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link com.google.api.services.calendar.Calendar$Acl} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.google.calendar.AbstractGoogleCalendarTestSupport#hasCredentials",
           disabledReason = "Google Calendar credentials were not provided")
public class CalendarAclIT extends AbstractGoogleCalendarTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarAclIT.class);
    private static final String PATH_PREFIX
            = GoogleCalendarApiCollection.getCollection().getApiName(CalendarAclApiMethod.class).getName();

    @Test
    public void testList() {
        // using String message body for single parameter "calendarId"
        final com.google.api.services.calendar.model.Acl result = requestBody("direct://LIST", getCalendar().getId());

        // should have at least one rule (reader, owner, etc.) for the calendar
        // or we wouldn't be able to view it!
        assertTrue(result.getItems().size() > 0);
        LOG.debug("list: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for delete
                from("direct://DELETE").to("google-calendar://" + PATH_PREFIX + "/delete");

                // test route for get
                from("direct://GET").to("google-calendar://" + PATH_PREFIX + "/get");

                // test route for insert
                from("direct://INSERT").to("google-calendar://" + PATH_PREFIX + "/insert");

                // test route for list
                from("direct://LIST").to("google-calendar://" + PATH_PREFIX + "/list?inBody=calendarId");

                // test route for patch
                from("direct://PATCH").to("google-calendar://" + PATH_PREFIX + "/patch");

                // test route for update
                from("direct://UPDATE").to("google-calendar://" + PATH_PREFIX + "/update");

                // test route for watch
                from("direct://WATCH").to("google-calendar://" + PATH_PREFIX + "/watch");

            }
        };
    }
}
