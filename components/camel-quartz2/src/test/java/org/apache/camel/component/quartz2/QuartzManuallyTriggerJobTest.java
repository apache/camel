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

import java.util.ArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.matchers.GroupMatcher;

/**
 * This test the  CronTrigger as a timer endpoint in a route.
 * @version 
 */
public class QuartzManuallyTriggerJobTest extends BaseQuartzTest {

    @Test
    public void testQuartzCronRoute() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        QuartzComponent component = context.getComponent("quartz2", QuartzComponent.class);
        Scheduler scheduler = component.getScheduler();
        
        // collect all jobKeys of this route (ideally only one).
        ArrayList<JobKey> jobKeys = new ArrayList<JobKey>();
        for (String group : scheduler.getJobGroupNames()) {
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(group))) {
                jobKeys.add(jobKey);
            }
        }     
        
        JobDataMap jobDataMap = scheduler.getJobDetail(jobKeys.get(0)).getJobDataMap();
        
        // trigger job manually
        scheduler.triggerJob(jobKeys.get(0), jobDataMap);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("quartz2://MyTimer?cron=05+00+00+*+*+?").to("mock:result");
            }
        };
    }
}