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
package org.apache.camel.component.quartz;

import org.apache.camel.Endpoint;
import org.junit.Test;
import org.quartz.CronTrigger;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

/**
 * @version 
 */
public class QuartzEndpointConfigureTest extends BaseQuartzTest {

    @Test
    public void testConfigureGroupAndName() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myName?trigger.repeatCount=3");
        Trigger trigger = endpoint.getTrigger();
        assertEquals("getName()", "myName", trigger.getName());
        assertEquals("getGroup()", "myGroup", trigger.getGroup());
        assertEquals("getJobName", "quartz-" + endpoint.getId(), endpoint.getJobName()); // default job name

        SimpleTrigger simpleTrigger = assertIsInstanceOf(SimpleTrigger.class, trigger);
        assertEquals("getRepeatCount()", 3, simpleTrigger.getRepeatCount());
    }

    @Test
    public void testConfigureName() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myName");
        Trigger trigger = endpoint.getTrigger();
        assertEquals("getName()", "myName", trigger.getName());
        assertEquals("getGroup()", "Camel", trigger.getGroup());
        assertEquals("getJobName", "quartz-" + endpoint.getId(), endpoint.getJobName()); // default job name
    }

    @Test
    public void testConfigureCronExpression() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myTimerName?cron=0+0/5+12-18+?+*+MON-FRI");
        CronTrigger trigger = assertIsInstanceOf(CronTrigger.class, endpoint.getTrigger());
        assertEquals("getName()", "myTimerName", trigger.getName());
        assertEquals("getGroup()", "myGroup", trigger.getGroup());
        assertEquals("cron expression", "0 0/5 12-18 ? * MON-FRI", trigger.getCronExpression());
        assertEquals("getJobName", "quartz-" + endpoint.getId(), endpoint.getJobName()); // default job name
    }

    @Test
    public void testConfigureAnotherCronExpression() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myTimerName?cron=0+0+*+*+*+?");
        CronTrigger trigger = assertIsInstanceOf(CronTrigger.class, endpoint.getTrigger());
        assertEquals("getName()", "myTimerName", trigger.getName());
        assertEquals("getGroup()", "myGroup", trigger.getGroup());
        assertEquals("cron expression", "0 0 * * * ?", trigger.getCronExpression());
        assertEquals("getJobName", "quartz-" + endpoint.getId(), endpoint.getJobName()); // default job name
    }

    @Test
    public void testConfigureJobName() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myTimerName?job.name=hadrian&cron=0+0+*+*+*+?");
        CronTrigger trigger = assertIsInstanceOf(CronTrigger.class, endpoint.getTrigger());
        assertEquals("getName()", "myTimerName", trigger.getName());
        assertEquals("getGroup()", "myGroup", trigger.getGroup());
        assertEquals("cron expression", "0 0 * * * ?", trigger.getCronExpression());
        assertEquals("getJobName", "hadrian", endpoint.getJobName());
    }

    @Test
    public void testConfigureNoDoubleSlashNoCron() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz:myGroup/myTimerName");
        Trigger trigger = endpoint.getTrigger();
        assertEquals("getName()", "myTimerName", trigger.getName());
        assertEquals("getGroup()", "myGroup", trigger.getGroup());
    }

    @Test
    public void testConfigureNoDoubleSlashQuestionCron() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz:myGroup/myTimerName?cron=0+0+*+*+*+?");
        CronTrigger trigger = assertIsInstanceOf(CronTrigger.class, endpoint.getTrigger());
        assertEquals("getName()", "myTimerName", trigger.getName());
        assertEquals("getGroup()", "myGroup", trigger.getGroup());
        assertEquals("cron expression", "0 0 * * * ?", trigger.getCronExpression());
    }

    @Override
    protected QuartzEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(QuartzEndpoint.class, endpoint);
    }
}