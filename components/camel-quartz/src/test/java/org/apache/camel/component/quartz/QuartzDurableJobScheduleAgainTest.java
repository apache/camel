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
import org.junit.jupiter.api.Test;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quartz keeps a durable job (durableJob=true) when its trigger is removed, as the endpoint does when it stops
 * (deleteJob=true). The endpoint must schedule the job again when it starts, instead of failing with
 * ObjectAlreadyExistsException.
 */
class QuartzDurableJobScheduleAgainTest extends BaseQuartzTest {

    private static final TriggerKey TRIGGER_KEY = TriggerKey.triggerKey("myTimer", "durable");
    private static final JobKey JOB_KEY = JobKey.jobKey("myTimer", "durable");

    @Test
    void durableJobScheduledAgainWhenTheRouteIsAddedAgain() throws Exception {
        context.addRoutes(route());
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();

        // a reload: the endpoint of the removed route unschedules its trigger, and Quartz keeps the durable job
        context.getRouteController().stopRoute("durable");
        context.removeRoute("durable");
        Scheduler scheduler = context.getComponent("quartz", QuartzComponent.class).getScheduler();
        assertNull(scheduler.getTrigger(TRIGGER_KEY));
        assertTrue(scheduler.checkExists(JOB_KEY));

        context.addRoutes(route());

        // removing the route removed its mock endpoint too, the route added again has a new one
        MockEndpoint again = getMockEndpoint("mock:result");
        again.expectedMinimumMessageCount(1);
        again.assertIsSatisfied();
        assertNotNull(scheduler.getTrigger(TRIGGER_KEY));
    }

    @Test
    void durableJobLeftWithoutTriggerScheduledAgain() throws Exception {
        // the durable job without its trigger, as a persistent job store keeps it after the application stopped
        Scheduler scheduler = context.getComponent("quartz", QuartzComponent.class).getScheduler();
        scheduler.addJob(JobBuilder.newJob(CamelJob.class).withIdentity(JOB_KEY).storeDurably()
                .usingJobData(QuartzConstants.QUARTZ_ENDPOINT_URI, "quartz://durable/myTimer?durableJob=true")
                .build(), false);

        context.addRoutes(route());
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.assertIsSatisfied();

        // the job of the endpoint replaced the stored one
        assertEquals(context.getRoute("durable").getEndpoint().getEndpointUri(),
                scheduler.getJobDetail(JOB_KEY).getJobDataMap().getString(QuartzConstants.QUARTZ_ENDPOINT_URI));
    }

    private static RouteBuilder route() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("quartz://durable/myTimer?durableJob=true&trigger.repeatInterval=100").routeId("durable")
                        .to("mock:result");
            }
        };
    }
}
