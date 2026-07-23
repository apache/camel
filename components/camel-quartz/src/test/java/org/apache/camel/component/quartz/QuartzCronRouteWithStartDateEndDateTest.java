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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;

/**
 * This test the CronTrigger as a timer endpoint in a route.
 */
public class QuartzCronRouteWithStartDateEndDateTest extends BaseQuartzTest {

    @Test
    public void testQuartzCronRouteWithStartDateEndDateTest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(2);

        // use timed assertion — the trigger starts 5s in the future and
        // fires for 4 seconds, so messages arrive between ~5s and ~9s
        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);
        assertThat(mock.getReceivedExchanges().size() <= 5, CoreMatchers.is(true));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
                calendar.add(Calendar.SECOND, 5);
                Date startDate = calendar.getTime();
                calendar.add(Calendar.SECOND, 4);
                Date endDate = calendar.getTime();

                // triggers every 1th second at precise 00,01,02,03..59 with startAt and endAt 4 seconds apart.
                // give plenty of headroom (5s) for route setup before the trigger window opens,
                // so that Quartz scheduler initialization doesn't eat into the firing window on loaded CI
                fromF("quartz://myGroup/myTimerName?cron=0/1 * * * * ?&trigger.startAt=%s&trigger.endAt=%s",
                        dateFormat.format(startDate), dateFormat.format(endDate)).to("mock:result");
            }
        };
    }
}
