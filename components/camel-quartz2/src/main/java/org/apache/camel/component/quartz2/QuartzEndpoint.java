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
package org.apache.camel.component.quartz2;

import java.util.Date;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.EndpointHelper;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * Provides a scheduled delivery of messages using the Quartz 2.x scheduler.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "quartz2", title = "Quartz2", syntax = "quartz2:groupName/triggerName", consumerOnly = true, consumerClass = QuartzComponent.class, label = "scheduling")
public class QuartzEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(QuartzEndpoint.class);
    private TriggerKey triggerKey;
    private LoadBalancer consumerLoadBalancer;
    // An internal variables to track whether a job has been in scheduler or not, and has it paused or not.
    private final AtomicBoolean jobAdded = new AtomicBoolean(false);
    private final AtomicBoolean jobPaused = new AtomicBoolean(false);

    @UriPath(description = "The quartz group name to use. The combination of group name and timer name should be unique.", defaultValue = "Camel")
    private String groupName;
    @UriPath @Metadata(required = "true")
    private String triggerName;
    @UriParam
    private String cron;
    @UriParam
    private boolean stateful;
    @UriParam(label = "scheduler")
    private boolean fireNow;
    @UriParam(defaultValue = "true")
    private boolean deleteJob = true;
    @UriParam
    private boolean pauseJob;
    @UriParam
    private boolean durableJob;
    @UriParam
    private boolean recoverableJob;
    @UriParam(label = "scheduler", defaultValue = "500")
    private long triggerStartDelay = 500;
    @UriParam(label = "scheduler")
    private int startDelayedSeconds;
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
        return triggerKey.getName();
    }

    public String getTriggerName() {
        return triggerKey.getName();
    }

    /**
     * The quartz timer name to use. The combination of group name and timer name should be unique.
     */
    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    public String getCron() {
        return cron;
    }

    public boolean isStateful() {
        return stateful;
    }

    public boolean isFireNow() {
        return fireNow;
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
     * If set to true, then the trigger automatically pauses when route stop.
     * Else if set to false, it will remain in scheduler. When set to false, it will also mean user may reuse
     * pre-configured trigger with camel Uri. Just ensure the names match.
     * Notice you cannot have both deleteJob and pauseJob set to true.
     */
    public void setPauseJob(boolean pauseJob) {
        this.pauseJob = pauseJob;
    }

    /**
     * In case of scheduler has already started, we want the trigger start slightly after current time to
     * ensure endpoint is fully started before the job kicks in.
     */
    public void setTriggerStartDelay(long triggerStartDelay) {
        this.triggerStartDelay = triggerStartDelay;
    }

    /**
     * If set to true, then the trigger automatically delete when route stop.
     * Else if set to false, it will remain in scheduler. When set to false, it will also mean user may reuse
     * pre-configured trigger with camel Uri. Just ensure the names match.
     * Notice you cannot have both deleteJob and pauseJob set to true.
     */
    public void setDeleteJob(boolean deleteJob) {
        this.deleteJob = deleteJob;
    }

    /**
     * If it is true will fire the trigger when the route is start when using SimpleTrigger.
     */
    public void setFireNow(boolean fireNow) {
        this.fireNow = fireNow;
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
     * Instructs the scheduler whether or not the job should be re-executed if a 'recovery' or 'fail-over' situation is encountered.
     */
    public void setRecoverableJob(boolean recoverableJob) {
        this.recoverableJob = recoverableJob;
    }

    public boolean isUsingFixedCamelContextName() {
        return usingFixedCamelContextName;
    }

    /**
     * If it is true, JobDataMap uses the CamelContext name directly to reference the CamelContext,
     * if it is false, JobDataMap uses use the CamelContext management name which could be changed during the deploy time.
     */
    public void setUsingFixedCamelContextName(boolean usingFixedCamelContextName) {
        this.usingFixedCamelContextName = usingFixedCamelContextName;
    }

    public LoadBalancer getConsumerLoadBalancer() {
        if (consumerLoadBalancer == null) {
            consumerLoadBalancer = new RoundRobinLoadBalancer();
        }
        return consumerLoadBalancer;
    }

    public void setConsumerLoadBalancer(LoadBalancer consumerLoadBalancer) {
        this.consumerLoadBalancer = consumerLoadBalancer;
    }


    public Map<String, Object> getTriggerParameters() {
        return triggerParameters;
    }

    /**
     * To configure additional options on the trigger.
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

    public int getStartDelayedSeconds() {
        return startDelayedSeconds;
    }

    /**
     * Seconds to wait before starting the quartz scheduler.
     */
    public void setStartDelayedSeconds(int startDelayedSeconds) {
        this.startDelayedSeconds = startDelayedSeconds;
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
    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        if (isDeleteJob() && isPauseJob()) {
            throw new IllegalArgumentException("Cannot have both options deleteJob and pauseJob enabled");
        }
        if (ObjectHelper.isNotEmpty(customCalendar)) {
            getComponent().getScheduler().addCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR, customCalendar, true, false);
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

        if (triggerExisted) {
            // Reschedule job if trigger settings were changed
            if (hasTriggerChanged(oldTrigger, trigger)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            }
        } else {
            try {
                // Schedule it now. Remember that scheduler might not be started it, but we can schedule now.
                scheduler.scheduleJob(jobDetail, trigger);
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

        if (LOG.isInfoEnabled()) {
            LOG.info("Job {} (triggerType={}, jobClass={}) is scheduled. Next fire date is {}",
                    new Object[] {trigger.getKey(), trigger.getClass().getSimpleName(),
                            jobDetail.getJobClass().getSimpleName(), trigger.getNextFireTime()});
        }

        // Increase camel job count for this endpoint
        AtomicInteger number = (AtomicInteger) scheduler.getContext().get(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT);
        if (number != null) {
            number.incrementAndGet();
        }

        jobAdded.set(true);
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
        Trigger result;
        Date startTime = new Date();
        if (getComponent().getScheduler().isStarted()) {
            startTime = new Date(System.currentTimeMillis() + triggerStartDelay);
        }
        if (cron != null) {
            LOG.debug("Creating CronTrigger: {}", cron);
            String timeZone = (String)triggerParameters.get("timeZone");
            if (timeZone != null) {
                if (ObjectHelper.isNotEmpty(customCalendar)) {
                    result = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .startAt(startTime)
                        .withSchedule(cronSchedule(cron)
                        .withMisfireHandlingInstructionFireAndProceed()
                        .inTimeZone(TimeZone.getTimeZone(timeZone)))
                        .modifiedByCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR)
                        .build();
                } else {
                    result = TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey)
                            .startAt(startTime)
                            .withSchedule(cronSchedule(cron)
                            .withMisfireHandlingInstructionFireAndProceed()
                            .inTimeZone(TimeZone.getTimeZone(timeZone)))
                            .build();
                }
                jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_CRON_TIMEZONE, timeZone);
            } else {
                if (ObjectHelper.isNotEmpty(customCalendar)) {
                    result = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .startAt(startTime)
                        .withSchedule(cronSchedule(cron)
                        .withMisfireHandlingInstructionFireAndProceed())
                        .modifiedByCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR)
                        .build();
                } else {
                    result = TriggerBuilder.newTrigger()
                            .withIdentity(triggerKey)
                            .startAt(startTime)
                            .withSchedule(cronSchedule(cron)
                            .withMisfireHandlingInstructionFireAndProceed())
                            .build();
                }
            }

            // enrich job map with details
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_TYPE, "cron");
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION, cron);
        } else {
            LOG.debug("Creating SimpleTrigger.");
            int repeat = SimpleTrigger.REPEAT_INDEFINITELY;
            String repeatString = (String) triggerParameters.get("repeatCount");
            if (repeatString != null) {
                repeat = EndpointHelper.resolveParameter(getCamelContext(), repeatString, Integer.class);
                // need to update the parameters
                triggerParameters.put("repeatCount", repeat);
            }

            // default use 1 sec interval
            long interval = 1000;
            String intervalString = (String) triggerParameters.get("repeatInterval");
            if (intervalString != null) {
                interval = EndpointHelper.resolveParameter(getCamelContext(), intervalString, Long.class);
                // need to update the parameters
                triggerParameters.put("repeatInterval", interval);
            }
            TriggerBuilder<SimpleTrigger> triggerBuilder;
            if (ObjectHelper.isNotEmpty(customCalendar)) {
                triggerBuilder = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .startAt(startTime)
                    .withSchedule(simpleSchedule().withMisfireHandlingInstructionFireNow()
                            .withRepeatCount(repeat).withIntervalInMilliseconds(interval)).modifiedByCalendar(QuartzConstants.QUARTZ_CAMEL_CUSTOM_CALENDAR);
            } else {
                triggerBuilder = TriggerBuilder.newTrigger()
                        .withIdentity(triggerKey)
                        .startAt(startTime)
                        .withSchedule(simpleSchedule().withMisfireHandlingInstructionFireNow()
                                .withRepeatCount(repeat).withIntervalInMilliseconds(interval));
            }

            if (fireNow) {
                triggerBuilder = triggerBuilder.startNow();
            }

            result = triggerBuilder.build();

            // enrich job map with details
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_TYPE, "simple");
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_COUNTER, repeat);
            jobDetail.getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_INTERVAL, interval);
        }

        if (triggerParameters != null && triggerParameters.size() > 0) {
            LOG.debug("Setting user extra triggerParameters {}", triggerParameters);
            setProperties(result, triggerParameters);
        }

        LOG.debug("Created trigger={}", result);
        return result;
    }

    private JobDetail createJobDetail() throws Exception {
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
            LOG.debug("Setting user extra jobParameters {}", jobParameters);
            setProperties(result, jobParameters);
        }

        LOG.debug("Created jobDetail={}", result);
        return result;
    }

    @Override
    public QuartzComponent getComponent() {
        return (QuartzComponent)super.getComponent();
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
        getConsumerLoadBalancer().addProcessor(quartzConsumer.getProcessor());
        if (!jobAdded.get()) {
            addJobInScheduler();
        } else {
            resumeTrigger();
        }
    }

    public void onConsumerStop(QuartzConsumer quartzConsumer) throws Exception {
        getConsumerLoadBalancer().removeProcessor(quartzConsumer.getProcessor());
        if (jobAdded.get()) {
            pauseTrigger();
        }
    }
}
