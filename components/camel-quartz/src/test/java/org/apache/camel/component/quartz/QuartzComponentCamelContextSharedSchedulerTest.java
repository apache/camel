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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.hamcrest.CoreMatchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDetail;
import org.quartz.Scheduler;

public class QuartzComponentCamelContextSharedSchedulerTest {

    private DefaultCamelContext camel1;
    private DefaultCamelContext camel2;

    @Before
    public void setUp() throws Exception {
        camel1 = new DefaultCamelContext();
        camel1.setName("camel-1");
        camel1.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myGroup/myTimerName?cron=0/2+*+*+*+*+?").to("mock:one");
            }
        });
        camel1.start();

        camel2 = new DefaultCamelContext();
        camel2.setName("camel-2");

        Scheduler camel1Scheduler = camel1.getComponent("quartz", QuartzComponent.class).getScheduler();
        QuartzComponent camel2QuartzComponent = camel2.getComponent("quartz", QuartzComponent.class);
        camel2QuartzComponent.setScheduler(camel1Scheduler);

        camel2.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("quartz://myOtherGroup/myOtherTimerName?cron=0/1+*+*+*+*+?").to("mock:two");
            }
        });

        camel2.start();
    }

    @After
    public void tearDown() throws Exception {
        camel1.stop();
        camel2.stop();
    }

    @Test
    public void testTwoCamelContext() throws Exception {
        MockEndpoint mock1 = camel1.getEndpoint("mock:one", MockEndpoint.class);
        mock1.expectedMinimumMessageCount(2);

        MockEndpoint mock2 = camel2.getEndpoint("mock:two", MockEndpoint.class);
        mock2.expectedMinimumMessageCount(6);
        mock1.assertIsSatisfied();

        JobDetail detail = mock1.getReceivedExchanges().get(0).getIn().getHeader("jobDetail", JobDetail.class);
        Assert.assertThat(detail.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION).equals("0/2 * * * * ?"), CoreMatchers.is(true));

        camel1.stop();

        mock2.assertIsSatisfied();

        detail = mock2.getReceivedExchanges().get(0).getIn().getHeader("jobDetail", JobDetail.class);
        Assert.assertThat(detail.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION).equals("0/1 * * * * ?"), CoreMatchers.is(true));

        camel2.stop();
    }
}
