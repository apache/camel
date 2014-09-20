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
package org.apache.camel.pollconsumer.quartz2;

import java.util.TimeZone;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.component.quartz2.QuartzComponent;
import org.apache.camel.component.quartz2.QuartzConstants;
import org.apache.camel.component.quartz2.QuartzHelper;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A quartz based {@link ScheduledPollConsumerScheduler} which uses a {@link CronTrigger} to define when the
 * poll should be triggered.
 */
public class QuartzScheduledPollConsumerScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzScheduledPollConsumerScheduler.class);
    private Scheduler quartzScheduler;
    private CamelContext camelContext;
    private String routeId;
    private Consumer consumer;
    private Runnable runnable;
    private String cron;
    private String triggerId;
    private String triggerGroup = "QuartzScheduledPollConsumerScheduler";
    private TimeZone timeZone = TimeZone.getDefault();
    private volatile CronTrigger trigger;
    private volatile JobDetail job;

    @Override
    public void onInit(Consumer consumer) {
        this.consumer = consumer;
        // find the route of the consumer
        for (Route route : consumer.getEndpoint().getCamelContext().getRoutes()) {
            if (route.getConsumer() == consumer) {
                this.routeId = route.getId();
                break;
            }
        }
    }

    @Override
    public void scheduleTask(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void unscheduleTask() {
        if (trigger != null) {
            LOG.debug("Unscheduling trigger: {}", trigger.getKey());
            try {
                quartzScheduler.unscheduleJob(trigger.getKey());
            } catch (SchedulerException e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public void startScheduler() {
        // the quartz component starts the scheduler
    }

    @Override
    public boolean isSchedulerStarted() {
        try {
            return quartzScheduler != null && quartzScheduler.isStarted();
        } catch (SchedulerException e) {
            return false;
        }
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public Scheduler getQuartzScheduler() {
        return quartzScheduler;
    }

    public void setQuartzScheduler(Scheduler scheduler) {
        this.quartzScheduler = scheduler;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
    }

    public String getTriggerGroup() {
        return triggerGroup;
    }

    public void setTriggerGroup(String triggerGroup) {
        this.triggerGroup = triggerGroup;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notEmpty(cron, "cron", this);

        if (quartzScheduler == null) {
            // get the scheduler form the quartz component
            QuartzComponent quartz = getCamelContext().getComponent("quartz2", QuartzComponent.class);
            setQuartzScheduler(quartz.getScheduler());
        }

        JobDataMap map = new JobDataMap();
        // do not store task as its not serializable, if we have route id
        if (routeId != null) {
            map.put("routeId", routeId);
        } else {
            map.put("task", runnable);
        }
        map.put(QuartzConstants.QUARTZ_TRIGGER_TYPE, "cron");
        map.put(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION, getCron());
        map.put(QuartzConstants.QUARTZ_TRIGGER_CRON_TIMEZONE, getTimeZone().getID());

        job = JobBuilder.newJob(QuartzScheduledPollConsumerJob.class)
                .usingJobData(map)
                .build();

        // store additional information on job such as camel context etc
        QuartzHelper.updateJobDataMap(getCamelContext(), job, null);

        String id = triggerId;
        if (id == null) {
            id = "trigger-" + getCamelContext().getUuidGenerator().generateUuid();
        }

        trigger = TriggerBuilder.newTrigger()
                .withIdentity(id, triggerGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(getCron()).inTimeZone(getTimeZone()))
                .build();

        LOG.debug("Scheduling job: {} with trigger: {}", job, trigger.getKey());
        quartzScheduler.scheduleJob(job, trigger);
    }

    @Override
    protected void doStop() throws Exception {
        if (trigger != null) {
            LOG.debug("Unscheduling trigger: {}", trigger.getKey());
            quartzScheduler.unscheduleJob(trigger.getKey());
        }
    }

    @Override
    protected void doShutdown() throws Exception {
    }

}
