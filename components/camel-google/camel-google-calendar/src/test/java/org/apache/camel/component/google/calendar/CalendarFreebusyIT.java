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
import java.util.List;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.FreeBusyRequest;
import com.google.api.services.calendar.model.FreeBusyRequestItem;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.calendar.internal.CalendarFreebusyApiMethod;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The class source won't be generated again if the generator MOJO finds it under src/test/java.
 */
@EnabledIf(value = "org.apache.camel.component.google.calendar.AbstractGoogleCalendarTestSupport#hasCredentials",
           disabledReason = "Google Calendar credentials were not provided")
public class CalendarFreebusyIT extends AbstractGoogleCalendarTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarFreebusyIT.class);
    private static final String PATH_PREFIX
            = GoogleCalendarApiCollection.getCollection().getApiName(CalendarFreebusyApiMethod.class).getName();

    @Test
    public void testQuery() {
        // using com.google.api.services.calendar.model.FreeBusyRequest message
        // body for single parameter "content"
        com.google.api.services.calendar.model.FreeBusyRequest request = new FreeBusyRequest();
        List<FreeBusyRequestItem> items = new ArrayList<>();
        items.add(new FreeBusyRequestItem().setId(getCalendar().getId()));
        request.setItems(items);

        request.setTimeMin(DateTime.parseRfc3339("2014-11-10T20:45:30-00:00"));
        request.setTimeMax(DateTime.parseRfc3339("2014-11-10T21:45:30-00:00"));

        final com.google.api.services.calendar.model.FreeBusyResponse result = requestBody("direct://QUERY", request);

        assertNotNull(result, "query result");
        LOG.debug("query: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for query
                from("direct://QUERY").to("google-calendar://" + PATH_PREFIX + "/query?inBody=content");

            }
        };
    }
}
