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
package org.apache.camel.component.quartz;

import java.util.Date;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.quartz.Calendar;
import org.quartz.Scheduler;
import org.quartz.impl.calendar.HolidayCalendar;

/**
 * This test a timer endpoint in a route with Custom calendar.
 */
public class QuartzCustomCalendarFireTest extends BaseQuartzTest {

    @Test
    public void testQuartzCustomCronRouteNoFire() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(0);

        QuartzComponent component = context.getComponent("quartz", QuartzComponent.class);
        Scheduler scheduler = component.getScheduler();

        Calendar c = scheduler.getCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR);
        Date now = new Date();
        java.util.Calendar tomorrow = java.util.Calendar.getInstance();
        tomorrow.setTime(now);
        tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
        assertEquals(false, c.isTimeIncluded(tomorrow.getTimeInMillis()));
        assertEquals(true, c.isTimeIncluded(now.getTime()));
        assertMockEndpointsSatisfied();
    }

    @BindToRegistry("calendar")
    public HolidayCalendar loadCalendar() throws Exception {

        HolidayCalendar cal = new HolidayCalendar();
        java.util.Calendar tomorrow = java.util.Calendar.getInstance();
        tomorrow.setTime(new Date());
        tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
        cal.addExcludedDate(tomorrow.getTime());

        return cal;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("quartz://MyTimer?customCalendar=#calendar&cron=05+00+00+*+*+?").to("mock:result");
            }
        };
    }
}
