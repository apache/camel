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
import org.apache.camel.component.google.calendar.internal.CalendarSettingsApiMethod;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link com.google.api.services.calendar.Calendar$Settings} APIs.
 */
public class CalendarSettingsIntegrationTest extends AbstractGoogleCalendarTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarSettingsIntegrationTest.class);
    private static final String PATH_PREFIX = GoogleCalendarApiCollection.getCollection().getApiName(CalendarSettingsApiMethod.class).getName();

    @Test
    public void testGet() throws Exception {
        // using String message body for single parameter "setting"
        final com.google.api.services.calendar.model.Setting result = requestBody("direct://GET", "timezone");

        assertNotNull("get result", result);
        LOG.debug("get: " + result);
    }

    @Test
    public void testList() throws Exception {
        final com.google.api.services.calendar.model.Settings result = requestBody("direct://LIST", null);

        assertNotNull("list result", result);
        LOG.debug("list: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // test route for get
                from("direct://GET").to("google-calendar://" + PATH_PREFIX + "/get?inBody=setting");

                // test route for list
                from("direct://LIST").to("google-calendar://" + PATH_PREFIX + "/list");

                // test route for watch
                from("direct://WATCH").to("google-calendar://" + PATH_PREFIX + "/watch?inBody=contentChannel");

            }
        };
    }
}
