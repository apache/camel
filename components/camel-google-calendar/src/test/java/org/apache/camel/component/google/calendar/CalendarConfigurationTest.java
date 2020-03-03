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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.calendar.internal.CalendarCalendarsApiMethod;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalendarConfigurationTest extends AbstractGoogleCalendarTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarConfigurationTest.class);
    private static final String PATH_PREFIX = GoogleCalendarApiCollection.getCollection().getApiName(CalendarCalendarsApiMethod.class).getName();
    private static final String TEST_URI = "google-calendar://" + PATH_PREFIX + "/get?clientId=a&clientSecret=b&applicationName=c&accessToken=d&refreshToken=e";

    @Before
    @Override    
    public void createTestCalendar() {
    }

    @After
    @Override
    public void deleteTestCalendar() {
    }
    
    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = new DefaultCamelContext(createCamelRegistry());

        // add GoogleCalendarComponent to Camel context but don't set up configuration
        final GoogleCalendarComponent component = new GoogleCalendarComponent(context);
        context.addComponent("google-calendar", component);

        return context;
    }
    
    @Test
    public void testConfiguration() throws Exception {
        GoogleCalendarEndpoint endpoint = getMandatoryEndpoint(TEST_URI, GoogleCalendarEndpoint.class);
        GoogleCalendarConfiguration configuration = endpoint.getConfiguration();
        assertNotNull(configuration);
        assertEquals("a", configuration.getClientId());
        assertEquals("b", configuration.getClientSecret());
        assertEquals("c", configuration.getApplicationName());
        assertEquals("d", configuration.getAccessToken());
        assertEquals("e", configuration.getRefreshToken());        
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct://COPY").to(TEST_URI);
            }
        };
    }
}
