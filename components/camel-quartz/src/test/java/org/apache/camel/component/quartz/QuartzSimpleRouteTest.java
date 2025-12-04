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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

/**
 * This not only set SimpleTrigger as a timer endpoint in a route, and also test the trigger.XXX properties setter.
 */
public class QuartzSimpleRouteTest extends BaseQuartzTest {

    @Test
    public void testQuartzCronRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        MockEndpoint.assertIsSatisfied(context);
        Trigger trigger = mock.getReceivedExchanges().get(0).getIn().getHeader("trigger", Trigger.class);
        assertInstanceOf(SimpleTrigger.class, trigger, "trigger should be a CronTrigger");

        JobDetail detail = mock.getReceivedExchanges().get(0).getIn().getHeader("jobDetail", JobDetail.class);
        assertEquals(CamelJob.class, detail.getJobClass());

        assertEquals("simple", detail.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE));
        assertEquals("-1", detail.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_COUNTER));
        assertEquals("100", detail.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_INTERVAL));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("quartz://myGroup/myTimerName?trigger.repeatInterval=100&trigger.repeatCount=-1")
                        .to("mock:result");
            }
        };
    }
}
