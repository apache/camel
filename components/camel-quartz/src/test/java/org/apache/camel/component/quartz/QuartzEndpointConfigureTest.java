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

import org.apache.camel.Endpoint;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QuartzEndpointConfigureTest extends BaseQuartzTest {

    @Test
    public void testConfigureGroupAndName() throws Exception {
        QuartzEndpoint endpoint
                = resolveMandatoryEndpoint("quartz://myGroup/myName?trigger.repeatCount=3&trigger.repeatInterval=100");

        Scheduler scheduler = endpoint.getComponent().getScheduler();
        TriggerKey triggerKey = endpoint.getTriggerKey();
        Trigger trigger = scheduler.getTrigger(triggerKey);
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(triggerKey.getName(), triggerKey.getGroup()));

        assertEquals("myName", triggerKey.getName(), "getName()");
        assertEquals("myGroup", triggerKey.getGroup(), "getGroup()");
        assertEquals("myName", jobDetail.getKey().getName(), "getJobName");
        assertEquals("myGroup", jobDetail.getKey().getGroup(), "getJobGroup");

        SimpleTrigger simpleTrigger = assertIsInstanceOf(SimpleTrigger.class, trigger);
        assertEquals(3, simpleTrigger.getRepeatCount(), "getRepeatCount()");
    }

    @Test
    public void testConfigureName() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myName");

        Scheduler scheduler = endpoint.getComponent().getScheduler();
        TriggerKey triggerKey = endpoint.getTriggerKey();
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(triggerKey.getName(), triggerKey.getGroup()));

        assertEquals("myName", triggerKey.getName(), "getName()");
        assertEquals("Camel_" + context.getManagementName(), triggerKey.getGroup(), "getGroup()");
        assertEquals("myName", jobDetail.getKey().getName(), "getJobName");
        assertEquals("Camel_" + context.getManagementName(), jobDetail.getKey().getGroup(), "getJobGroup");
    }

    @Test
    public void testConfigureCronExpression() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myTimerName?cron=0+0/5+12-18+?+*+MON-FRI");

        Scheduler scheduler = endpoint.getComponent().getScheduler();
        TriggerKey triggerKey = endpoint.getTriggerKey();
        Trigger trigger = scheduler.getTrigger(triggerKey);
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(triggerKey.getName(), triggerKey.getGroup()));

        assertEquals("myTimerName", triggerKey.getName(), "getName()");
        assertEquals("myGroup", triggerKey.getGroup(), "getGroup()");
        assertEquals("myTimerName", jobDetail.getKey().getName(), "getJobName");
        assertEquals("myGroup", jobDetail.getKey().getGroup(), "getJobGroup");

        assertIsInstanceOf(CronTrigger.class, trigger);
        CronTrigger cronTrigger = (CronTrigger) trigger;
        assertEquals("0 0/5 12-18 ? * MON-FRI", cronTrigger.getCronExpression(), "cron expression");
    }

    @Test
    public void testConfigureAnotherCronExpression() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myTimerName?cron=0+0+*+*+*+?");

        Scheduler scheduler = endpoint.getComponent().getScheduler();
        TriggerKey triggerKey = endpoint.getTriggerKey();
        Trigger trigger = scheduler.getTrigger(triggerKey);
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(triggerKey.getName(), triggerKey.getGroup()));

        assertEquals("myTimerName", triggerKey.getName(), "getName()");
        assertEquals("myGroup", triggerKey.getGroup(), "getGroup()");
        assertEquals("myTimerName", jobDetail.getKey().getName(), "getJobName");
        assertEquals("myGroup", jobDetail.getKey().getGroup(), "getJobGroup");

        assertIsInstanceOf(CronTrigger.class, trigger);
        CronTrigger cronTrigger = (CronTrigger) trigger;
        assertEquals("0 0 * * * ?", cronTrigger.getCronExpression(), "cron expression");
    }

    @Test
    public void testConfigureJobName() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myTimerName?job.name=hadrian&cron=0+0+*+*+*+?");

        Scheduler scheduler = endpoint.getComponent().getScheduler();
        TriggerKey triggerKey = endpoint.getTriggerKey();
        Trigger trigger = scheduler.getTrigger(triggerKey);
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey("hadrian", triggerKey.getGroup()));

        assertEquals("myTimerName", triggerKey.getName(), "getName()");
        assertEquals("myGroup", triggerKey.getGroup(), "getGroup()");
        assertEquals("hadrian", jobDetail.getKey().getName(), "getJobName");
        assertEquals("myGroup", jobDetail.getKey().getGroup(), "getJobGroup");

        assertIsInstanceOf(CronTrigger.class, trigger);
    }

    @Test
    public void testConfigureNoDoubleSlashNoCron() {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz:myGroup/myTimerName");

        TriggerKey triggerKey = endpoint.getTriggerKey();
        assertEquals("myTimerName", triggerKey.getName(), "getName()");
        assertEquals("myGroup", triggerKey.getGroup(), "getGroup()");
    }

    @Test
    public void testConfigureNoDoubleSlashQuestionCron() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz:myGroup/myTimerName?cron=0+0+*+*+*+?");

        Scheduler scheduler = endpoint.getComponent().getScheduler();
        TriggerKey triggerKey = endpoint.getTriggerKey();
        Trigger trigger = scheduler.getTrigger(triggerKey);
        JobDetail jobDetail = scheduler.getJobDetail(JobKey.jobKey(triggerKey.getName(), triggerKey.getGroup()));

        assertEquals("myTimerName", triggerKey.getName(), "getName()");
        assertEquals("myGroup", triggerKey.getGroup(), "getGroup()");
        assertEquals("myTimerName", jobDetail.getKey().getName(), "getJobName");
        assertEquals("myGroup", jobDetail.getKey().getGroup(), "getJobGroup");

        assertIsInstanceOf(CronTrigger.class, trigger);
        CronTrigger cronTrigger = (CronTrigger) trigger;
        assertEquals("0 0 * * * ?", cronTrigger.getCronExpression(), "cron expression");
    }

    @Test
    public void testConfigureDeleteJob() {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz:myGroup/myTimerName?cron=0+0+*+*+*+?");
        assertEquals("0 0 * * * ?", endpoint.getCron(), "cron expression");
        assertTrue(endpoint.isDeleteJob(), "deleteJob");

        endpoint = resolveMandatoryEndpoint("quartz:myGroup/myTimerName2?cron=1+0+*+*+*+?&deleteJob=false");
        assertEquals("1 0 * * * ?", endpoint.getCron(), "cron expression");
        assertFalse(endpoint.isDeleteJob(), "deleteJob");
    }

    @Override
    protected QuartzEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(QuartzEndpoint.class, endpoint);
    }
}
