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
package org.apache.camel.component.quartz2;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Trigger;

/**
 * This test the  CronTrigger as a timer endpoint in a route.
 */
public class QuartzStatefulJobRouteTest extends BaseQuartzTest {

    @Test
    public void testQuartzCronRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        assertMockEndpointsSatisfied();

        Trigger trigger = mock.getReceivedExchanges().get(0).getIn().getHeader("trigger", Trigger.class);
        Assert.assertThat(trigger instanceof CronTrigger, CoreMatchers.is(true));

        JobDetail detail = mock.getReceivedExchanges().get(0).getIn().getHeader("jobDetail", JobDetail.class);
        Assert.assertThat(detail.getJobClass().equals(StatefulCamelJob.class), CoreMatchers.is(true));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // triggers every 2th second at precise 00,02,04,06..58
                // notice we must use + as space when configured using URI parameter
                from("quartz2://myGroup/myTimerName?cron=0/2+*+*+*+*+?&stateful=true").to("mock:result");
            }
        };
    }
}