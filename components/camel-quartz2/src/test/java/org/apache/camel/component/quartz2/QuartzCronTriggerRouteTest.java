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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.quartz.JobDetail;

public class QuartzCronTriggerRouteTest extends CamelTestSupport {

    @Test
    public void testQuartzCronRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(3);

        assertMockEndpointsSatisfied();

        JobDetail job = mock.getReceivedExchanges().get(0).getIn().getHeader("jobDetail", JobDetail.class);
        assertNotNull(job);

        assertEquals("cron", job.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_TYPE));
        assertEquals("UTC", job.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_CRON_TIMEZONE));
        assertEquals("0/2 * * * * ?", job.getJobDataMap().get(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("quartz2://myGroup/myTimerName?cron=0/2+*+*+*+*+?&trigger.timeZone=UTC").to("mock:result");
            }
        };
    }
}
