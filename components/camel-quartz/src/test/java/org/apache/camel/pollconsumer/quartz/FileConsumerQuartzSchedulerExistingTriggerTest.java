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
package org.apache.camel.pollconsumer.quartz;

import java.nio.file.Path;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.component.quartz.QuartzHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route whose trigger is already in the Quartz scheduler when it starts, with the same cron, starts and polls with
 * that trigger: the trigger kept by deleteJob=false when the route stopped, or a trigger left in a persistent job store
 * by an application that did not stop gracefully.
 */
class FileConsumerQuartzSchedulerExistingTriggerTest extends CamelTestSupport {

    private static final String CRON = "0/1 * * * * ?";
    private static final TriggerKey TRIGGER_KEY = new TriggerKey("myId", "myGroup");

    @TempDir
    Path testDirectory;

    @Test
    void testRestartWithDeleteJobFalse() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader(TestSupport.fileUri(testDirectory), "Hello World", Exchange.FILE_NAME, "hello.txt");
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context);

        context.getRouteController().stopRoute("foo");
        // the trigger is kept as deleteJob=false
        assertNotNull(scheduler().getTrigger(TRIGGER_KEY));
        MockEndpoint.resetMocks(context);

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader(TestSupport.fileUri(testDirectory), "Bye World", Exchange.FILE_NAME, "bye.txt");
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context);
        assertEquals("Started", context.getRouteController().getRouteStatus("foo").toString());
    }

    @Test
    void testStartWithTriggerLeftInScheduler() throws Exception {
        // the trigger and job of route foo, as left in a persistent job store by an earlier run of the application
        JobDetail job = JobBuilder.newJob(QuartzScheduledPollConsumerJob.class)
                .usingJobData("routeId", "foo").build();
        QuartzHelper.updateJobDataMap(context, job, null);
        Trigger trigger = TriggerBuilder.newTrigger().withIdentity("myId", "myGroup")
                .withSchedule(CronScheduleBuilder.cronSchedule(CRON)).build();
        scheduler().scheduleJob(job, trigger);

        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBodyAndHeader(TestSupport.fileUri(testDirectory), "Hello World", Exchange.FILE_NAME, "hello.txt");
        context.getRouteController().startRoute("foo");
        MockEndpoint.assertIsSatisfied(context);
        assertEquals("Started", context.getRouteController().getRouteStatus("foo").toString());
    }

    @Test
    void testTriggerOfAnotherRoute() throws Exception {
        // route bar (deleteJob=true) used the trigger key first, and deleted its trigger when it stopped
        context.getRouteController().startRoute("bar");
        context.getRouteController().stopRoute("bar");
        assertNull(scheduler().getTrigger(TRIGGER_KEY));
        // route foo then used the same key, and kept its trigger when it stopped (deleteJob=false)
        context.getRouteController().startRoute("foo");
        context.getRouteController().stopRoute("foo");

        // route bar uses the trigger id and group that route foo kept
        Exception e = assertThrows(Exception.class, () -> context.getRouteController().startRoute("bar"));
        Throwable cause = e;
        while (cause.getCause() != null && !(cause instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof IllegalArgumentException, "Unexpected exception: " + e);
        assertTrue(cause.getMessage().contains("is already used by route: foo"), cause.getMessage());
        // the failed start of route bar did not unschedule the trigger of route foo
        assertNotNull(scheduler().getTrigger(TRIGGER_KEY));
    }

    private Scheduler scheduler() {
        return context.getComponent("quartz", QuartzComponent.class).getScheduler();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String options = "?scheduler=quartz&scheduler.cron=" + CRON.replace(' ', '+')
                                 + "&scheduler.triggerGroup=myGroup&scheduler.triggerId=myId";
                from(TestSupport.fileUri(testDirectory, options + "&scheduler.deleteJob=false"))
                        .routeId("foo").autoStartup(false)
                        .to("mock:result");
                from(TestSupport.fileUri(testDirectory, "other" + options + "&scheduler.deleteJob=true"))
                        .routeId("bar").autoStartup(false)
                        .to("mock:other");
            }
        };
    }
}
