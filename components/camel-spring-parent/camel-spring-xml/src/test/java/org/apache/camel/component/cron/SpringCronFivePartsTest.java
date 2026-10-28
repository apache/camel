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
package org.apache.camel.component.cron;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cron component documents schedules of five parts (no seconds), such as 0/2 * * * ?; the Spring implementation
 * must accept them like the Quartz one does.
 */
class SpringCronFivePartsTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testFivePartsSchedule() throws Exception {
        assertEquals("0 0/2 * * * ?", springCron("0/2 * * * ?"));
    }

    @Test
    void testFivePartsUnixSchedule() throws Exception {
        assertEquals("0 */5 * * * *", springCron("*/5 * * * *"));
    }

    @Test
    void testFivePartsUnixScheduleDayOfWeekNames() throws Exception {
        assertEquals("0 0 9 * * MON-FRI", springCron("0 9 * * MON-FRI"));
    }

    @Test
    void testFivePartsUnixScheduleBothDaysRejected() {
        // Unix cron fires on the 1st, the 15th and every Monday; Spring would fire only on a Monday that is the 1st or
        // the 15th, so the schedule fails as before instead of firing on other days
        Exception e = assertThrows(Exception.class, () -> springCron("30 6 1,15 * MON"));
        assertTrue(rootCauseMessage(e).contains("would fire on other days"), rootCauseMessage(e));
    }

    @Test
    void testFivePartsUnixScheduleDayOfWeekStepRejected() {
        // Unix */2 is SUN, TUE, THU, SAT; Spring counts from Monday
        Exception e = assertThrows(Exception.class, () -> springCron("0 9 * * */2"));
        assertTrue(rootCauseMessage(e).contains("would fire on other days"), rootCauseMessage(e));
    }

    private static String rootCauseMessage(Throwable e) {
        while (e.getCause() != null) {
            e = e.getCause();
        }
        return e.getMessage();
    }

    private String springCron(String schedule) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("cron:tab?schedule=" + schedule).routeId("cron").to("mock:result");
            }
        });
        context.start();

        Endpoint endpoint = context.getRoute("cron").getEndpoint();
        while (endpoint instanceof DelegateEndpoint delegate) {
            endpoint = delegate.getEndpoint();
        }
        return (String) ((ScheduledPollEndpoint) endpoint).getSchedulerProperties().get("cron");
    }
}
