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
import org.junit.jupiter.api.Test;
import org.quartz.Calendar;
import org.quartz.Scheduler;
import org.quartz.impl.calendar.HolidayCalendar;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Regression test: two endpoints with different customCalendars must each register under a unique scheduler name so
 * that the second addCalendar() does not overwrite the first.
 */
public class QuartzCustomCalendarCollisionTest extends BaseQuartzTest {

    @BindToRegistry("calendarA")
    public HolidayCalendar loadCalendarA() {
        HolidayCalendar cal = new HolidayCalendar();
        // Exclude today — this calendar should suppress triggers
        cal.addExcludedDate(new Date());
        return cal;
    }

    @BindToRegistry("calendarB")
    public HolidayCalendar loadCalendarB() {
        // No excluded dates — always-open calendar
        return new HolidayCalendar();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("quartz://TimerA?customCalendar=#calendarA&cron=05+00+00+*+*+?").routeId("routeA").to("mock:a");
                from("quartz://TimerB?customCalendar=#calendarB&cron=05+00+00+*+*+?").routeId("routeB").to("mock:b");
            }
        };
    }

    @Test
    public void testEachEndpointRegistersItsOwnCalendar() throws Exception {
        QuartzComponent component = context.getComponent("quartz", QuartzComponent.class);
        Scheduler scheduler = component.getScheduler();

        QuartzEndpoint endpointA = (QuartzEndpoint) context.getRoute("routeA").getConsumer().getEndpoint();
        QuartzEndpoint endpointB = (QuartzEndpoint) context.getRoute("routeB").getConsumer().getEndpoint();

        Calendar calA = scheduler.getCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR + "_" + endpointA.getGroupName()
                                              + "_" + endpointA.getTriggerName());
        Calendar calB = scheduler.getCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR + "_" + endpointB.getGroupName()
                                              + "_" + endpointB.getTriggerName());

        assertNotNull(calA, "Calendar for TimerA must be registered under its own unique name");
        assertNotNull(calB, "Calendar for TimerB must be registered under its own unique name");
        assertNotSame(calA, calB, "Each endpoint must register a distinct calendar object");
    }
}
