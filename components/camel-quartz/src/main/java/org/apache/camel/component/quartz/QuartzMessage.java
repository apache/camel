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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultMessage;

import org.quartz.JobExecutionContext;
import org.quartz.Trigger;

/**
 * @version 
 */
public class QuartzMessage extends DefaultMessage {
    private final JobExecutionContext jobExecutionContext;

    public QuartzMessage(Exchange exchange, JobExecutionContext jobExecutionContext) {
        super(exchange.getContext());
        this.jobExecutionContext = jobExecutionContext;
        setExchange(exchange);
        // do not set body as it should be null
    }

    public JobExecutionContext getJobExecutionContext() {
        return jobExecutionContext;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        super.populateInitialHeaders(map);
        if (jobExecutionContext != null) {
            map.put("calendar", jobExecutionContext.getCalendar());
            map.put("fireTime", jobExecutionContext.getFireTime());
            map.put("jobDetail", jobExecutionContext.getJobDetail());
            map.put("jobInstance", jobExecutionContext.getJobInstance());
            map.put("jobRunTime", jobExecutionContext.getJobRunTime());
            map.put("mergedJobDataMap", jobExecutionContext.getMergedJobDataMap());
            map.put("nextFireTime", jobExecutionContext.getNextFireTime());
            map.put("previousFireTime", jobExecutionContext.getPreviousFireTime());
            map.put("refireCount", jobExecutionContext.getRefireCount());
            map.put("result", jobExecutionContext.getResult());
            map.put("scheduledFireTime", jobExecutionContext.getScheduledFireTime());
            map.put("scheduler", jobExecutionContext.getScheduler());
            Trigger trigger = jobExecutionContext.getTrigger();
            map.put("trigger", trigger);
            map.put("triggerName", trigger.getName());
            map.put("triggerGroup", trigger.getGroup());
        }
    }

    @Override
    public DefaultMessage newInstance() {
        return new QuartzMessage(getExchange(), jobExecutionContext);
    }
}
