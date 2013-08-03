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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.util.EndpointHelper;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.SimpleScheduleBuilder.simpleSchedule;


/**
 * This endpoint represent each job to be created in scheduler. When consumer is started or stopped, it will
 * call back into doConsumerStart()/Stop() to pause/resume the scheduler trigger.
 *
 */
public class QuartzEndpoint extends DefaultEndpoint {
    private static final transient Logger LOG = LoggerFactory.getLogger(QuartzEndpoint.class);
    private TriggerKey triggerKey;
    private String cron;
    private LoadBalancer consumerLoadBalancer;
    private Map<String, Object> triggerParameters;
    private Map<String, Object> jobParameters;
    private boolean stateful;
    private boolean fireNow;
    private boolean deleteJob = true;
    /** In case of scheduler has already started, we want the trigger start slightly after current time to
     * ensure endpoint is fully started before the job kicks in. */
    private long triggerStartDelay = 500; // in millis second

    // An internal variables to track whether a job has been in scheduler or not, and has it paused or not.
    private AtomicBoolean jobAdded = new AtomicBoolean(false);
    private AtomicBoolean jobPaused = new AtomicBoolean(false);
    
    public QuartzEndpoint(String uri, QuartzComponent quartzComponent) {
        super(uri, quartzComponent);
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

    public void setTriggerStartDelay(long triggerStartDelay) {
        this.triggerStartDelay = triggerStartDelay;
    }

    public void setDeleteJob(boolean deleteJob) {
        this.deleteJob = deleteJob;
    }

    public void setFireNow(boolean fireNow) {
        this.fireNow = fireNow;
    }

    public void setStateful(boolean stateful) {
        this.stateful = stateful;
    }

    public void setTriggerParameters(Map<String, Object> triggerParameters) {
        this.triggerParameters = triggerParameters;
    }

    public void setJobParameters(Map<String, Object> jobParameters) {
        this.jobParameters = jobParameters;
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

    public void setCron(String cron) {
        this.cron = cron;
    }

    public TriggerKey getTriggerKey() {
        return triggerKey;
    }

    public void setTriggerKey(TriggerKey triggerKey) {
        this.triggerKey = triggerKey;
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
        return false;
    }

    @Override
    protected void doStart() throws Exception {
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
        }

        // Decrement camel job count for this endpoint
        AtomicInteger number = (AtomicInteger) scheduler.getContext().get(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT);
        number.decrementAndGet();
    }

    private void addJobInScheduler() throws Exception {
        // Add or use existing trigger to/from scheduler
        Scheduler scheduler = getComponent().getScheduler();
        JobDetail jobDetail = null;
        Trigger trigger = scheduler.getTrigger(triggerKey);
        if (trigger == null) {
            jobDetail = createJobDetail();
            trigger = createTrigger();

            updateJobDataMap(jobDetail);

            // Schedule it now. Remember that scheduler might not be started it, but we can schedule now.
            Date nextFireDate = scheduler.scheduleJob(jobDetail, trigger);
            LOG.info("Job {} (triggerType={}, jobClass={}) is scheduled. Next fire date is {}",
                     new Object[] {trigger.getKey(), trigger.getClass().getSimpleName(),
                          jobDetail.getJobClass().getSimpleName(), nextFireDate});
        } else {
            ensureNoDupTriggerKey();

            // Update existing jobDetails with current endpoint data to jobDataMap.
            jobDetail = scheduler.getJobDetail(trigger.getJobKey());
            updateJobDataMap(jobDetail);
            scheduler.addJob(jobDetail, true);
            Date nextFireDate = trigger.getNextFireTime();
            LOG.info("Reuse existing Job {} (triggerType={}, jobType={}) is scheduled. Next fire date is {}",
                     new Object[] {trigger.getKey(), trigger.getClass().getSimpleName(),
                                   jobDetail.getJobClass().getSimpleName(), nextFireDate});
        }

        // Increase camel job count for this endpoint
        AtomicInteger number = (AtomicInteger) scheduler.getContext().get(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT);
        number.incrementAndGet();

        jobAdded.set(true);
    }

    private void ensureNoDupTriggerKey() {
        for (Route route : getCamelContext().getRoutes()) {
            if (route.getEndpoint() instanceof QuartzEndpoint) {
                QuartzEndpoint quartzEndpoint = (QuartzEndpoint) route.getEndpoint();
                TriggerKey checkTriggerKey = quartzEndpoint.getTriggerKey();
                if (triggerKey.equals(checkTriggerKey)) {
                    throw new IllegalArgumentException("Trigger key " + triggerKey + " is already in used by " + quartzEndpoint);
                }
            }
        }
    }

    private void updateJobDataMap(JobDetail jobDetail) {
        // Store this camelContext name into the job data
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        String camelContextName = getCamelContext().getManagementName();
        String endpointUri = getEndpointUri();
        LOG.debug("Adding camelContextName={}, endpintUri={} into job data map.", camelContextName, endpointUri);
        jobDataMap.put(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME, camelContextName);
        jobDataMap.put(QuartzConstants.QUARTZ_ENDPOINT_URI, endpointUri);
    }

    private Trigger createTrigger() throws Exception {
        Trigger result = null;
        Date startTime = new Date();
        if (getComponent().getScheduler().isStarted()) {
            startTime = new Date(System.currentTimeMillis() + triggerStartDelay);
        }
        if (cron != null) {
            LOG.debug("Creating CronTrigger: {}", cron);
            result = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .startAt(startTime)
                    .withSchedule(cronSchedule(cron).withMisfireHandlingInstructionFireAndProceed())
                    .build();
        } else {
            LOG.debug("Creating SimpleTrigger.");
            TriggerBuilder<SimpleTrigger> triggerBuilder = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .startAt(startTime)
                    .withSchedule(simpleSchedule().withMisfireHandlingInstructionFireNow());

            // Enable trigger to fire now by setting startTime in the past.
            if (fireNow) {
                String intervalString = (String) triggerParameters.get("repeatInterval");
                if (intervalString != null) {
                    long interval = Long.valueOf(intervalString);
                    triggerBuilder.startAt(new Date(System.currentTimeMillis() - interval));
                }
            }

            result = triggerBuilder.build();
        }

        if (triggerParameters != null && triggerParameters.size() > 0) {
            LOG.debug("Setting user extra triggerParameters {}", triggerParameters);
            setProperties(result, triggerParameters);
        }

        LOG.debug("Created trigger={}", result);
        return result;
    }

    private void setProperties(Object bean, Map<String, Object> parameters) throws Exception {
        EndpointHelper.setReferenceProperties(getCamelContext(), bean, parameters);
        EndpointHelper.setProperties(getCamelContext(), bean, parameters);
    }

    private JobDetail createJobDetail() throws Exception {
        // Camel endpoint timer will assume one to one for JobDetail and Trigger, so let's use same name as trigger
        String name = triggerKey.getName();
        String group = triggerKey.getGroup();
        Class<? extends Job> jobClass = stateful ? StatefulCamelJob.class : CamelJob.class;
        LOG.debug("Creating new {}.", jobClass.getSimpleName());

        JobDetail result = JobBuilder.newJob(jobClass)
                .withIdentity(name, group)
                .build();

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
        if (jobPaused.get()) {
            return;
        }
        jobPaused.set(true);

        Scheduler scheduler = getComponent().getScheduler();
        if (scheduler != null) {
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
