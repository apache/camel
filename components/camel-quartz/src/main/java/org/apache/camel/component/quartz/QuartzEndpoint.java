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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.quartz.Calendar;
import org.quartz.CronTrigger;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.ObjectAlreadyExistsException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.spi.OperableTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * Schedule sending of messages using the Quartz 2.x scheduler.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "quartz", title = "Quartz", syntax = "quartz:groupName/triggerName",
             remote = false, consumerOnly = true, category = { Category.SCHEDULING })
public class QuartzEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzEndpoint.class);

    private TriggerKey triggerKey;

    private volatile AsyncProcessor processor;

    // An internal variables to track whether a job has been in scheduler or not, and has it paused or not.
    private final AtomicBoolean jobAdded = new AtomicBoolean();
    private final AtomicBoolean jobPaused = new AtomicBoolean();

    @UriPath(description = "The quartz group name to use. The combination of group name and trigger name should be unique.",
             defaultValue = "Camel")
    private String groupName;
    @UriPath(description = "The quartz trigger name to use. The combination of group name and trigger name should be unique.")
    @Metadata(required = true)
    private String triggerName;
    @UriParam
    private String cron;
    @UriParam
    private boolean stateful;
    @UriParam(label = "advanced")
    private boolean ignoreExpiredNextFireTime;
    @UriParam(defaultValue = "true")
    private boolean deleteJob = true;
    @UriParam
    private boolean pauseJob;
    @UriParam
    private boolean durableJob;
    @UriParam
    private boolean recoverableJob;
    @UriParam(label = "scheduler", defaultValue = "500", javaType = "java.time.Duration")
    private long triggerStartDelay = 500;
    @UriParam(label = "scheduler", defaultValue = "true")
    private boolean autoStartScheduler = true;
    @UriParam(label = "advanced")
    private boolean usingFixedCamelContextName;
    @UriParam(label = "advanced")
    private boolean prefixJobNameWithEndpointId;
    @UriParam(prefix = "trigger.", multiValue = true, label = "advanced")
    private Map<String, Object> triggerParameters;
    @UriParam(prefix = "job.", multiValue = true, label = "advanced")
    private Map<String, Object> jobParameters;
    @UriParam(label = "advanced")
    private Calendar customCalendar;

    public QuartzEndpoint(String uri, QuartzComponent quartzComponent) {
        super(uri, quartzComponent);
    }

    public String getGroupName() {
        return triggerKey.getGroup();
    }

    public String getTriggerName() {
        return triggerKey.getName();
    }

    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    public String getCron() {
        return cron;
    }

    public boolean isStateful() {
        return stateful;
    }

    public boolean isIgnoreExpiredNextFireTime() {
        return ignoreExpiredNextFireTime;
    }

    /**
     * Whether to ignore quartz cannot schedule a trigger because the trigger will never fire in the future. This can
     * happen when using a cron trigger that are configured to only run in the past.
     *
     * By default, Quartz will fail to schedule the trigger and therefore fail to start the Camel route. You can set
     * this to true which then logs a WARN and then ignore the problem, meaning that the route will never fire in the
     * future.
     */
    public void setIgnoreExpiredNextFireTime(boolean ignoreExpiredNextFireTime) {
        this.ignoreExpiredNextFireTime = ignoreExpiredNextFireTime;
    }

    public long getTriggerStartDelay() {
        return triggerStartDelay;
    }

    public boolean isDeleteJob() {
        return deleteJob;
    }

    public boolean isPauseJob() {
        return pauseJob;
    }

    /**
     * If set to true, then the trigger automatically pauses when route stop. Else if set to false, it will remain in
     * scheduler. When set to false, it will also mean user may reuse pre-configured trigger with camel Uri. Just ensure
     * the names match. Notice you cannot have both deleteJob and pauseJob set to true.
     */
    public void setPauseJob(boolean pauseJob) {
        this.pauseJob = pauseJob;
    }

    /**
     * In case of scheduler has already started, we want the trigger start slightly after current time to ensure
     * endpoint is fully started before the job kicks in. Negative value shifts trigger start time in the past.
     */
    public void setTriggerStartDelay(long triggerStartDelay) {
        this.triggerStartDelay = triggerStartDelay;
    }

    /**
     * If set to true, then the trigger automatically delete when route stop. Else if set to false, it will remain in
     * scheduler. When set to false, it will also mean user may reuse pre-configured trigger with camel Uri. Just ensure
     * the names match. Notice you cannot have both deleteJob and pauseJob set to true.
     */
    public void setDeleteJob(boolean deleteJob) {
        this.deleteJob = deleteJob;
    }

    /**
     * Uses a Quartz @PersistJobDataAfterExecution and @DisallowConcurrentExecution instead of the default job.
     */
    public void setStateful(boolean stateful) {
        this.stateful = stateful;
    }

    public boolean isDurableJob() {
        return durableJob;
    }

    /**
     * Whether or not the job should remain stored after it is orphaned (no triggers point to it).
     */
    public void setDurableJob(boolean durableJob) {
        this.durableJob = durableJob;
    }

    public boolean isRecoverableJob() {
        return recoverableJob;
    }

    /**
     * Instructs the scheduler whether or not the job should be re-executed if a 'recovery' or 'fail-over' situation is
     * encountered.
     */
    public void setRecoverableJob(boolean recoverableJob) {
        this.recoverableJob = recoverableJob;
    }

    public boolean isUsingFixedCamelContextName() {
        return usingFixedCamelContextName;
    }

    /**
     * If it is true, JobDataMap uses the CamelContext name directly to reference the CamelContext, if it is false,
     * JobDataMap uses use the CamelContext management name which could be changed during the deploy time.
     */
    public void setUsingFixedCamelContextName(boolean usingFixedCamelContextName) {
        this.usingFixedCamelContextName = usingFixedCamelContextName;
    }

    public Map<String, Object> getTriggerParameters() {
        return triggerParameters;
    }

    /**
     * To configure additional options on the trigger. The parameter timeZone is supported if the cron option is
     * present. Otherwise the parameters repeatInterval and repeatCount are supported.
     * <p>
     * <b>Note:</b> When using repeatInterval values of 1000 or less, the first few events after starting the camel
     * context may be fired more rapidly than expected.
     * </p>
     */
    public void setTriggerParameters(Map<String, Object> triggerParameters) {
        this.triggerParameters = triggerParameters;
    }

    public Map<String, Object> getJobParameters() {
        return jobParameters;
    }

    /**
     * To configure additional options on the job.
     */
    public void setJobParameters(Map<String, Object> jobParameters) {
        this.jobParameters = jobParameters;
    }

    public boolean isAutoStartScheduler() {
        return autoStartScheduler;
    }

    /**
     * Whether or not the scheduler should be auto started.
     */
    public void setAutoStartScheduler(boolean autoStartScheduler) {
        this.autoStartScheduler = autoStartScheduler;
    }

    public boolean isPrefixJobNameWithEndpointId() {
        return prefixJobNameWithEndpointId;
    }

    /**
     * Whether the job name should be prefixed with endpoint id
     *
     * @param prefixJobNameWithEndpointId
     */
    public void setPrefixJobNameWithEndpointId(boolean prefixJobNameWithEndpointId) {
        this.prefixJobNameWithEndpointId = prefixJobNameWithEndpointId;
    }

    /**
     * Specifies a cron expression to define when to trigger.
     */
    public void setCron(String cron) {
        this.cron = cron;
    }

    public TriggerKey getTriggerKey() {
        return triggerKey;
    }

    public void setTriggerKey(TriggerKey triggerKey) {
        this.triggerKey = triggerKey;
    }

    public Calendar getCustomCalendar() {
        return customCalendar;
    }

    /**
     * Specifies a custom calendar to avoid specific range of date
     */
    public void setCustomCalendar(Calendar customCalendar) {
        this.customCalendar = customCalendar;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Quartz producer is not supported.");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        QuartzConsumer result = new QuartzConsumer(this, processor);
        configureConsumer(result);
        return result;
    }

    @Override
    protected void doStart() throws Exception {
        if (isDeleteJob() && isPauseJob()) {
            throw new IllegalArgumentException("Cannot have both options deleteJob and pauseJob enabled");
        }
        if (ObjectHelper.isNotEmpty(customCalendar)) {
            getComponent().getScheduler().addCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR, customCalendar, true,
                    false);
        }
        addJobInScheduler();
    }

    @Override
    protected void doStop() throws Exception {
        removeJobInScheduler();
    }

    private void removeJobInScheduler() throws Exception {
        Scheduler scheduler = getComponent().getScheduler();
        if (scheduler == null) {
            return;
        }

        if (deleteJob) {
            boolean isClustered = scheduler.getMetaData().isJobStoreClustered();
            if (!scheduler.isShutdown() && !isClustered) {
                LOG.info("Deleting job {}", triggerKey);
                scheduler.unscheduleJob(triggerKey);

                jobAdded.set(false);
            }
        } else if (pauseJob) {
            pauseTrigger();
        }

        // Decrement camel job count for this endpoint
        AtomicInteger number = (AtomicInteger) scheduler.getContext().get(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT);
        if (number != null) {
            number.decrementAndGet();
        }
    }

    private void addJobInScheduler() throws Exception {
        // Add or use existing trigger to/from scheduler
        Scheduler scheduler = getComponent().getScheduler();
        JobDetail jobDetail;
        Trigger oldTrigger = scheduler.getTrigger(triggerKey);
        boolean triggerExisted = oldTrigger != null;
        if (triggerExisted && !isRecoverableJob()) {
            ensureNoDupTriggerKey();
        }

        jobDetail = createJobDetail();
        Trigger trigger = createTrigger(jobDetail);

        QuartzHelper.updateJobDataMap(getCamelContext(), jobDetail, getEndpointUri(), isUsingFixedCamelContextName());

        boolean scheduled = true;
        if (triggerExisted) {
            // Reschedule job if trigger settings were changed
            if (hasTriggerChanged(oldTrigger, trigger)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            }
        } else {
            try {
                if (hasTriggerExpired(scheduler, trigger)) {
                    scheduled = false;
                    LOG.warn(
                            "Job {} (cron={}, triggerType={}, jobClass={}) not scheduled, because it will never fire in the future",
                            trigger.getKey(), cron, trigger.getClass().getSimpleName(),
                            jobDetail.getJobClass().getSimpleName());
                } else {
                    // Schedule it now. Remember that scheduler might not be started it, but we can schedule now.
                    scheduler.scheduleJob(jobDetail, trigger);
                }
            } catch (ObjectAlreadyExistsException ex) {
                // some other VM might may have stored the job & trigger in DB in clustered mode, in the mean time
                if (!(getComponent().isClustered())) {
                    throw ex;
                } else {
                    trigger = scheduler.getTrigger(triggerKey);
                    if (trigger == null) {
                        throw new SchedulerException("Trigger could not be found in quartz scheduler.");
                    }
                }
            }
        }

        if (scheduled) {
            if (LOG.isInfoEnabled()) {
                Object nextFireTime = trigger.getNextFireTime();
                if (nextFireTime != null) {
                    nextFireTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(nextFireTime);
                }
                LOG.info("Job {} (cron={}, triggerType={}, jobClass={}) is scheduled. Next fire date is {}",
                        trigger.getKey(), cron, trigger.getClass().getSimpleName(),
                        jobDetail.getJobClass().getSimpleName(), nextFireTime);
            }
        }

        // Increase camel job count for this endpoint
        AtomicInteger number = (AtomicInteger) scheduler.getContext().get(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT);
        if (number != null) {
            number.incrementAndGet();
        }

        jobAdded.set(true);
    }

    private boolean hasTriggerExpired(Scheduler scheduler, Trigger trigger) throws SchedulerException {
        Calendar cal = null;
        if (trigger.getCalendarName() != null) {
            cal = scheduler.getCalendar(trigger.getCalendarName());
        }
        OperableTrigger ot = (OperableTrigger) trigger;

        // check if current time is past the Trigger EndDate
        if (ot.getEndTime() != null && new Date().after(ot.getEndTime())) {
            return true;
        }
        // calculate whether the trigger can be triggered in the future
        Date ft = ot.computeFirstFireTime(cal);
        return (ft == null && ignoreExpiredNextFireTime);
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

    private void ensureNoDupTriggerKey() {
        for (Route route : getCamelContext().getRoutes()) {
            if (route.getEndpoint() instanceof QuartzEndpoint) {
                QuartzEndpoint quartzEndpoint = (QuartzEndpoint) route.getEndpoint();
                TriggerKey checkTriggerKey = quartzEndpoint.getTriggerKey();
                if (triggerKey.equals(checkTriggerKey)) {
                    throw new IllegalArgumentException("Trigger key " + triggerKey + " is already in use by " + quartzEndpoint);
                }
            }
        }
    }

    private Trigger createTrigger(JobDetail jobDetail) throws Exception {
        // use a defensive copy to keep the trigger parameters on the endpoint
        final Map<String, Object> copy = new HashMap<>(triggerParameters);

        final TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger().withIdentity(triggerKey);

        if (getComponent().getScheduler().isStarted() || triggerStartDelay < 0) {
            triggerBuilder.startAt(new Date(System.currentTimeMillis() + triggerStartDelay));
        }
        if (cron != null) {
            LOG.debug("Creating CronTrigger: {}", cron);
            final String startAt = (String) copy.get("startAt");
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz");
            if (startAt != null) {
                triggerBuilder.startAt(dateFormat.parse(startAt));
            }
            final String endAt = (String) copy.get("endAt");
            if (endAt != null) {
                triggerBuilder.endAt(dateFormat.parse(endAt));
            }
            final String timeZone = (String) copy.get("timeZone");
            if (timeZone != null) {
                if (ObjectHelper.isNotEmpty(customCalendar)) {
                    triggerBuilder
                            .withSchedule(cronSchedule(cron)
                                    .withMisfireHandlingInstructionFireAndProceed()
                                    .inTimeZone(TimeZone.getTimeZone(timeZone)))
                            .modifiedByCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR);
                } else {
                    triggerBuilder
                            .withSchedule(cronSchedule(cron)
                                    .withMisfireHandlingInstructionFireAndProceed()
                                    .inTimeZone(TimeZone.getTimeZone(timeZone)));
                }
                jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_CRON_TIMEZONE, timeZone);
            } else {
                if (ObjectHelper.isNotEmpty(customCalendar)) {
                    triggerBuilder
                            .withSchedule(cronSchedule(cron)
                                    .withMisfireHandlingInstructionFireAndProceed())
                            .modifiedByCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR);
                } else {
                    triggerBuilder
                            .withSchedule(cronSchedule(cron)
                                    .withMisfireHandlingInstructionFireAndProceed());
                }
            }

            // enrich job map with details
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_TYPE, "cron");
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION, cron);
        } else {
            LOG.debug("Creating SimpleTrigger.");
            int repeat = SimpleTrigger.REPEAT_INDEFINITELY;
            String repeatString = (String) copy.get("repeatCount");
            if (repeatString != null) {
                repeat = EndpointHelper.resolveParameter(getCamelContext(), repeatString, Integer.class);
                // need to update the parameters
                copy.put("repeatCount", repeat);
            }

            // default use 1 sec interval
            long interval = 1000;
            String intervalString = (String) copy.get("repeatInterval");
            if (intervalString != null) {
                interval = EndpointHelper.resolveParameter(getCamelContext(), intervalString, Long.class);
                // need to update the parameters
                copy.put("repeatInterval", interval);
            }
            if (ObjectHelper.isNotEmpty(customCalendar)) {
                triggerBuilder
                        .withSchedule(simpleSchedule().withMisfireHandlingInstructionFireNow()
                                .withRepeatCount(repeat).withIntervalInMilliseconds(interval))
                        .modifiedByCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR);
            } else {
                triggerBuilder
                        .withSchedule(simpleSchedule().withMisfireHandlingInstructionFireNow()
                                .withRepeatCount(repeat).withIntervalInMilliseconds(interval));
            }

            // enrich job map with details
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_TYPE, "simple");
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_COUNTER, String.valueOf(repeat));
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_INTERVAL, String.valueOf(interval));
        }

        final Trigger result = triggerBuilder.build();

        if (!copy.isEmpty()) {
            LOG.debug("Setting user extra triggerParameters {}", copy);
            setProperties(result, copy);
        }

        LOG.debug("Created trigger={}", result);
        return result;
    }

    private JobDetail createJobDetail() {
        // Camel endpoint timer will assume one to one for JobDetail and Trigger, so let's use same name as trigger
        String name = triggerKey.getName();
        String group = triggerKey.getGroup();
        Class<? extends Job> jobClass = stateful ? StatefulCamelJob.class : CamelJob.class;
        LOG.debug("Creating new {}.", jobClass.getSimpleName());

        JobBuilder builder = JobBuilder.newJob(jobClass)
                .withIdentity(name, group);

        if (durableJob) {
            builder = builder.storeDurably();
        }
        if (recoverableJob) {
            builder = builder.requestRecovery();
        }

        JobDetail result = builder.build();

        // Let user parameters to further set JobDetail properties.
        if (jobParameters != null && jobParameters.size() > 0) {
            // need to use a copy to keep the parameters on the endpoint
            Map<String, Object> copy = new HashMap<>(jobParameters);
            LOG.debug("Setting user extra jobParameters {}", copy);
            setProperties(result, copy);
        }

        LOG.debug("Created jobDetail={}", result);
        return result;
    }

    @Override
    public QuartzComponent getComponent() {
        return (QuartzComponent) super.getComponent();
    }

    public void pauseTrigger() throws Exception {
        Scheduler scheduler = getComponent().getScheduler();
        boolean isClustered = scheduler.getMetaData().isJobStoreClustered();

        if (jobPaused.get() || isClustered) {
            return;
        }

        jobPaused.set(true);
        if (!scheduler.isShutdown()) {
            LOG.info("Pausing trigger {}", triggerKey);
            scheduler.pauseTrigger(triggerKey);
        }
    }

    public void resumeTrigger() throws Exception {
        if (!jobPaused.get()) {
            return;
        }
        jobPaused.set(false);

        Scheduler scheduler = getComponent().getScheduler();
        if (scheduler != null) {
            LOG.info("Resuming trigger {}", triggerKey);
            scheduler.resumeTrigger(triggerKey);
        }
    }

    public void onConsumerStart(QuartzConsumer quartzConsumer) throws Exception {
        this.processor = quartzConsumer.getAsyncProcessor();
        if (!jobAdded.get()) {
            addJobInScheduler();
        } else {
            resumeTrigger();
        }
    }

    public void onConsumerStop(QuartzConsumer quartzConsumer) throws Exception {
        if (jobAdded.get()) {
            pauseTrigger();
        }
        this.processor = null;
    }

    AsyncProcessor getProcessor() {
        return this.processor;
    }
}
