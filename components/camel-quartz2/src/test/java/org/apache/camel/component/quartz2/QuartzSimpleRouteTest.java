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
import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

/**
 * This not only set SimpleTrigger as a timer endpoint in a route, and also test the trigger.XXX properties setter.
 * @version 
 */
public class QuartzSimpleRouteTest extends BaseQuartzTest {

    @Test
    public void testQuartzCronRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        assertMockEndpointsSatisfied();
        Trigger trigger = mock.getReceivedExchanges().get(0).getIn().getHeader("trigger", Trigger.class);
        Assert.assertThat(trigger instanceof SimpleTrigger, CoreMatchers.is(true));

        JobDetail detail = mock.getReceivedExchanges().get(0).getIn().getHeader("jobDetail", JobDetail.class);
        Assert.assertThat(detail.getJobClass().equals(CamelJob.class), CoreMatchers.is(true));

        Assert.assertThat(detail.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE).equals("simple"), CoreMatchers.is(true));
        Assert.assertThat(detail.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_COUNTER).equals(-1), CoreMatchers.is(true));
        Assert.assertThat(detail.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_INTERVAL).equals(2000L), CoreMatchers.is(true));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("quartz2://myGroup/myTimerName?trigger.repeatInterval=2000&trigger.repeatCount=-1").to("mock:result");
            }
        };
    }
}