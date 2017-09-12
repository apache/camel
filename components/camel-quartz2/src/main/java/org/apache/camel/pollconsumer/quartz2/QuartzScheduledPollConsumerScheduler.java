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
import org.apache.camel.NonManagedService;
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
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A quartz based {@link ScheduledPollConsumerScheduler} which uses a
 * {@link CronTrigger} to define when the poll should be triggered.
 */
public class QuartzScheduledPollConsumerScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler, NonManagedService {

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

        String id = triggerId;
        if (id == null) {
            id = "trigger-" + getCamelContext().getUuidGenerator().generateUuid();
        }

        CronTrigger existingTrigger = null;
        TriggerKey triggerKey = null;
        if (triggerId != null && triggerGroup != null) {
            triggerKey = new TriggerKey(triggerId, triggerGroup);
            existingTrigger = (CronTrigger)quartzScheduler.getTrigger(triggerKey);
        }

        // Is an trigger already exist for this triggerId ?
        if (existingTrigger == null) {
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

            job = JobBuilder.newJob(QuartzScheduledPollConsumerJob.class).usingJobData(map).build();

            // store additional information on job such as camel context etc
            QuartzHelper.updateJobDataMap(getCamelContext(), job, null);

            trigger = TriggerBuilder.newTrigger().withIdentity(id, triggerGroup)
                .withSchedule(CronScheduleBuilder.cronSchedule(getCron()).inTimeZone(getTimeZone()))
                .build();

            LOG.debug("Scheduling job: {} with trigger: {}", job, trigger.getKey());
            quartzScheduler.scheduleJob(job, trigger);
        } else {
            checkTriggerIsNonConflicting(existingTrigger);

            LOG.debug("Trigger with key {} is already present in scheduler. Only updating it.", triggerKey);
            job = quartzScheduler.getJobDetail(existingTrigger.getJobKey());
            JobDataMap jobData = job.getJobDataMap();
            jobData.put(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION, getCron());
            jobData.put(QuartzConstants.QUARTZ_TRIGGER_CRON_TIMEZONE, getTimeZone().getID());

            // store additional information on job such as camel context etc
            QuartzHelper.updateJobDataMap(getCamelContext(), job, null);
            LOG.debug("Updated jobData map to {}", jobData);

            trigger = existingTrigger.getTriggerBuilder()
                .withSchedule(CronScheduleBuilder.cronSchedule(getCron()).inTimeZone(getTimeZone()))
                .build();

            // Reschedule job if trigger settings were changed
            if (hasTriggerChanged(existingTrigger, trigger)) {
                LOG.debug("Re-scheduling job: {} with trigger: {}", job, trigger.getKey());
                quartzScheduler.rescheduleJob(triggerKey, trigger);
            } else {
                // Schedule it now. Remember that scheduler might not be started it, but we can schedule now.
                LOG.debug("Scheduling job: {} with trigger: {}", job, trigger.getKey());
                try {
                    // Schedule it now. Remember that scheduler might not be started it, but we can schedule now.
                    quartzScheduler.scheduleJob(job, trigger);
                } catch (ObjectAlreadyExistsException ex) {
                    // some other VM might may have stored the job & trigger in DB in clustered mode, in the mean time
                    QuartzComponent quartz = getCamelContext().getComponent("quartz2", QuartzComponent.class);
                    if (!(quartz.isClustered())) {
                        throw ex;
                    } else {
                        trigger = (CronTrigger) quartzScheduler.getTrigger(triggerKey);
                        if (trigger == null) {
                            throw new SchedulerException("Trigger could not be found in quartz scheduler.");
                        }
                    }
                }
            }
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Job {} (triggerType={}, jobClass={}) is scheduled. Next fire date is {}",
                new Object[] {trigger.getKey(), trigger.getClass().getSimpleName(),
                    job.getJobClass().getSimpleName(), trigger.getNextFireTime()});
        }
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

    private void checkTriggerIsNonConflicting(Trigger trigger) {
        JobDataMap jobDataMap = trigger.getJobDataMap();
        String routeIdFromTrigger = jobDataMap.getString("routeId");
        if (routeIdFromTrigger != null && !routeIdFromTrigger.equals(routeId)) {
            throw new IllegalArgumentException("Trigger key " + trigger.getKey() + " is already used by route: " + routeIdFromTrigger + ". Cannot re-use it for another route: " + routeId);
        }
    }

    private boolean hasTriggerChanged(Trigger oldTrigger, Trigger newTrigger) {
        if (newTrigger instanceof CronTrigger && oldTrigger instanceof CronTrigger) {
            CronTrigger newCron = (CronTrigger) newTrigger;
            CronTrigger oldCron = (CronTrigger) oldTrigger;
            return !newCron.getCronExpression().equals(oldCron.getCronExpression());
        } else if (newTrigger instanceof SimpleTrigger && oldTrigger instanceof SimpleTrigger) {
            SimpleTrigger newSimple = (SimpleTrigger) newTrigger;
            SimpleTrigger oldSimple = (SimpleTrigger) oldTrigger;
            return newSimple.getRepeatInterval() != oldSimple.getRepeatInterval()
                || newSimple.getRepeatCount() != oldSimple.getRepeatCount();
        } else {
            return !newTrigger.getClass().equals(oldTrigger.getClass()) || !newTrigger.equals(oldTrigger);
        }
    }

}
