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

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.component.quartz.QuartzConstants;
import org.apache.camel.component.quartz.QuartzHelper;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StringHelper;
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
 * A quartz based {@link ScheduledPollConsumerScheduler} which uses a {@link CronTrigger} to define when the poll should
 * be triggered.
 */
@Configurer
public class QuartzScheduledPollConsumerScheduler extends ServiceSupport
        implements ScheduledPollConsumerScheduler, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzScheduledPollConsumerScheduler.class);

    private Scheduler quartzScheduler;
    private CamelContext camelContext;
    private String routeId;
    private Runnable runnable;
    private String cron;
    private String triggerId;
    private Map<String, Object> triggerParameters;
    private Map<String, Object> jobParameters;
    private String triggerGroup = "QuartzScheduledPollConsumerScheduler";
    private TimeZone timeZone = TimeZone.getDefault();
    private boolean deleteJob = true;
    private volatile CronTrigger trigger;
    private volatile JobDetail job;
    private volatile boolean scheduled;
    private volatile TriggerKey rescheduleKey;
    // the trigger was already in the scheduler when this scheduler started (kept by deleteJob=false, or left in a
    // persistent job store), so it is not scheduled again
    private volatile boolean triggerExists;

    @Override
    public void onInit(Consumer consumer) {
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
            // no trigger when the start failed before it was set up (a trigger key used by another route)
            LOG.debug("Unscheduling trigger: {}", trigger.getKey());
        }
        try {
            unscheduleJob();
        } catch (SchedulerException e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void startScheduler() {
        if (trigger == null) {
            // the start of this scheduler failed before it set up the trigger (a trigger key used by another route);
            // the file consumer starts the scheduler also when its start failed, which must not hide that failure
            LOG.debug("Not scheduling as there is no trigger");
            return;
        }
        if (!scheduled) {
            try {
                if (rescheduleKey != null) {
                    LOG.debug("Re-scheduling job: {} with trigger: {}", job, trigger.getKey());
                    quartzScheduler.rescheduleJob(rescheduleKey, trigger);
                } else if (triggerExists) {
                    // the trigger (and its job) are already in the scheduler with the same cron: scheduling them
                    // again would fail with ObjectAlreadyExistsException, so use the stored trigger
                    TriggerKey triggerKey = trigger.getKey();
                    CronTrigger existing = (CronTrigger) quartzScheduler.getTrigger(triggerKey);
                    if (existing == null) {
                        // removed from the scheduler since this scheduler started
                        LOG.debug("Scheduling job: {} with trigger: {}", job, triggerKey);
                        quartzScheduler.scheduleJob(job, trigger);
                    } else {
                        LOG.debug("Job: {} with trigger: {} is already scheduled", job, triggerKey);
                        trigger = existing;
                    }
                } else {
                    LOG.debug("Scheduling job: {} with trigger: {}", job, trigger.getKey());
                    try {
                        quartzScheduler.scheduleJob(job, trigger);
                    } catch (ObjectAlreadyExistsException ex) {
                        QuartzComponent quartz = getCamelContext().getComponent("quartz", QuartzComponent.class);
                        if (!(quartz.isClustered())) {
                            throw ex;
                        } else {
                            TriggerKey triggerKey = trigger.getKey();
                            trigger = (CronTrigger) quartzScheduler.getTrigger(triggerKey);
                            if (trigger == null) {
                                throw new SchedulerException("Trigger could not be found in quartz scheduler.");
                            }
                        }
                    }
                }
                scheduled = true;
                if (LOG.isInfoEnabled()) {
                    LOG.info("Job {} (triggerType={}, jobClass={}) is scheduled. Next fire date is {}",
                            trigger.getKey(), trigger.getClass().getSimpleName(),
                            job.getJobClass().getSimpleName(), trigger.getNextFireTime());
                }
            } catch (SchedulerException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public boolean isSchedulerStarted() {
        return scheduled;
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

    public Map<String, Object> getTriggerParameters() {
        return triggerParameters;
    }

    public void setTriggerParameters(Map<String, Object> triggerParameters) {
        this.triggerParameters = triggerParameters;
    }

    public Map<String, Object> getJobParameters() {
        return jobParameters;
    }

    public void setJobParameters(Map<String, Object> jobParameters) {
        this.jobParameters = jobParameters;
    }

    public boolean isDeleteJob() {
        return deleteJob;
    }

    public void setDeleteJob(boolean deleteJob) {
        this.deleteJob = deleteJob;
    }

    @Override
    protected void doStart() throws Exception {
        StringHelper.notEmpty(cron, "cron", this);
        // special for cron where we replace + as space
        cron = cron.replace('+', ' ');

        if (quartzScheduler == null) {
            // get the scheduler form the quartz component
            QuartzComponent quartz = getCamelContext().getComponent("quartz", QuartzComponent.class);
            setQuartzScheduler(quartz.getScheduler());
        }

        String id = triggerId;
        if (id == null) {
            id = "trigger-" + getCamelContext().getUuidGenerator().generateUuid();
        }

        triggerExists = false;
        // the trigger of an earlier start: if this start fails (a trigger key used by another route), the stop that
        // follows must not unschedule the trigger with that key, which is now the other route's, and startScheduler
        // must not schedule it again
        trigger = null;
        CronTrigger existingTrigger = null;
        TriggerKey triggerKey = null;
        if (triggerId != null && triggerGroup != null) {
            triggerKey = new TriggerKey(triggerId, triggerGroup);
            existingTrigger = (CronTrigger) quartzScheduler.getTrigger(triggerKey);
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
            // Let user parameters to further set JobDetail properties.
            if (jobParameters != null && jobParameters.size() > 0) {
                // need to use a copy to keep the parameters
                Map<String, Object> copy = new HashMap<>(jobParameters);
                LOG.debug("Setting user extra jobParameters {}", copy);
                PropertyBindingSupport.bindProperties(camelContext, job, copy);
            }

            // store additional information on job such as camel context etc
            QuartzHelper.updateJobDataMap(getCamelContext(), job, null);

            trigger = TriggerBuilder.newTrigger().withIdentity(id, triggerGroup)
                    .withSchedule(CronScheduleBuilder.cronSchedule(getCron()).inTimeZone(getTimeZone()))
                    .build();
            if (triggerParameters != null && triggerParameters.size() > 0) {
                // need to use a copy to keep the parameters
                Map<String, Object> copy = new HashMap<>(triggerParameters);
                LOG.debug("Setting user extra triggerParameters {}", copy);
                PropertyBindingSupport.bindProperties(camelContext, trigger, copy);
            }
        } else {
            job = quartzScheduler.getJobDetail(existingTrigger.getJobKey());
            checkTriggerIsNonConflicting(existingTrigger, job);

            LOG.debug("Trigger with key {} is already present in scheduler. Only updating it.", triggerKey);
            JobDataMap jobData = job.getJobDataMap();
            jobData.put(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION, getCron());
            jobData.put(QuartzConstants.QUARTZ_TRIGGER_CRON_TIMEZONE, getTimeZone().getID());

            // store additional information on job such as camel context etc
            QuartzHelper.updateJobDataMap(getCamelContext(), job, null);
            LOG.debug("Updated jobData map to {}", jobData);

            trigger = existingTrigger.getTriggerBuilder()
                    .withSchedule(CronScheduleBuilder.cronSchedule(getCron()).inTimeZone(getTimeZone()))
                    .build();

            if (hasTriggerChanged(existingTrigger, trigger)) {
                rescheduleKey = triggerKey;
            } else {
                triggerExists = true;
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        unscheduleJob();
    }

    @Override
    protected void doShutdown() throws Exception {
    }

    private void unscheduleJob() throws SchedulerException {
        if (trigger != null && deleteJob) {
            boolean isClustered = quartzScheduler.getMetaData().isJobStoreClustered();
            if (!quartzScheduler.isShutdown() && !isClustered) {
                LOG.info("Deleting job {}", trigger.getKey());
                quartzScheduler.unscheduleJob(trigger.getKey());
            }
        }
        scheduled = false;
        rescheduleKey = null;
    }

    private void checkTriggerIsNonConflicting(Trigger trigger, JobDetail jobDetail) {
        // the route id is stored on the job (see doStart), not on the trigger
        JobDataMap jobDataMap = jobDetail != null ? jobDetail.getJobDataMap() : trigger.getJobDataMap();
        String routeIdFromTrigger = jobDataMap.getString("routeId");
        if (routeIdFromTrigger != null && !routeIdFromTrigger.equals(routeId)) {
            throw new IllegalArgumentException(
                    "Trigger key " + trigger.getKey() + " is already used by route: " + routeIdFromTrigger
                                               + ". Cannot re-use it for another route: " + routeId);
        }
    }

    private boolean hasTriggerChanged(Trigger oldTrigger, Trigger newTrigger) {
        if (newTrigger instanceof CronTrigger newCron && oldTrigger instanceof CronTrigger oldCron) {
            return !newCron.getCronExpression().equals(oldCron.getCronExpression());
        } else if (newTrigger instanceof SimpleTrigger newSimple && oldTrigger instanceof SimpleTrigger oldSimple) {
            return newSimple.getRepeatInterval() != oldSimple.getRepeatInterval()
                    || newSimple.getRepeatCount() != oldSimple.getRepeatCount();
        } else {
            return !newTrigger.getClass().equals(oldTrigger.getClass()) || !newTrigger.equals(oldTrigger);
        }
    }

}
