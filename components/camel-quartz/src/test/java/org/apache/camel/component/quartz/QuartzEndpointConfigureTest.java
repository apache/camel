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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;

import org.quartz.CronTrigger;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

/**
 * @version $Revision: 1.1 $
 */
public class QuartzEndpointConfigureTest extends ContextTestSupport {

    public void testConfigureGroupAndName() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myName?trigger.repeatCount=3");
        Trigger trigger = endpoint.getTrigger();
        assertEquals("getName()", "myName", trigger.getName());
        assertEquals("getGroup()", "myGroup", trigger.getGroup());

        SimpleTrigger simpleTrigger = assertIsInstanceOf(SimpleTrigger.class, trigger);
        assertEquals("getRepeatCount()", 3, simpleTrigger.getRepeatCount());
    }

    public void testConfigureName() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myName");
        Trigger trigger = endpoint.getTrigger();
        assertEquals("getName()", "myName", trigger.getName());
        assertEquals("getGroup()", "Camel", trigger.getGroup());
    }

    public void testConfigureCronExpression() throws Exception {
        QuartzEndpoint endpoint = resolveMandatoryEndpoint("quartz://myGroup/myTimerName/0/0/12/*/*/$");
        CronTrigger trigger = assertIsInstanceOf(CronTrigger.class, endpoint.getTrigger());
        assertEquals("getName()", "myTimerName", trigger.getName());
        assertEquals("getGroup()", "myGroup", trigger.getGroup());
        assertEquals("cron expression", "0 0 12 * * ?", trigger.getCronExpression());
    }

    @Override
    protected QuartzEndpoint resolveMandatoryEndpoint(String uri) {
        Endpoint endpoint = super.resolveMandatoryEndpoint(uri);
        return assertIsInstanceOf(QuartzEndpoint.class, endpoint);
    }
}