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

import java.util.Date;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ShutdownableService;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://activemq.apache.org/quartz.html">Quartz Endpoint</a>
 *
 * @version 
 */
@UriEndpoint(scheme = "quartz", title = "Quartz", syntax = "quartz:groupName/timerName", consumerOnly = true, consumerClass = QuartzConsumer.class, label = "scheduling")
public class QuartzEndpoint extends DefaultEndpoint implements ShutdownableService {
    private static final Logger LOG = LoggerFactory.getLogger(QuartzEndpoint.class);

    private LoadBalancer loadBalancer;
    private Trigger trigger;
    private JobDetail jobDetail = new JobDetail();
    private volatile boolean started;
    @UriPath(defaultValue = "Camel")
    private String groupName;
    @UriPath @Metadata(required = "true")
    private String timerName;
    @UriParam
    private boolean stateful;
    @UriParam(defaultValue = "true")
    private boolean deleteJob = true;
    @UriParam
    private boolean pauseJob;
    /** If it is true, the CamelContext name is used,
     *  if it is false, use the CamelContext management name which could be changed during the deploy time 
     **/
    @UriParam
    private boolean usingFixedCamelContextName;

    public QuartzEndpoint(final String endpointUri, final QuartzComponent component) {
        super(endpointUri, component);
        getJobDetail().setName("quartz-" + getId());
    }

    public void addTrigger(final Trigger trigger, final JobDetail detail) throws SchedulerException {
        // lets default the trigger name to the job name
        if (trigger.getName() == null) {
            trigger.setName(detail.getName());
        }
        // lets default the trigger group to the job group
        if (trigger.getGroup() == null) {
            trigger.setGroup(detail.getGroup());
        }
        // default start time to now if not specified
        if (trigger.getStartTime() == null) {
            trigger.setStartTime(new Date());
        }
        detail.getJobDataMap().put(QuartzConstants.QUARTZ_ENDPOINT_URI, getEndpointUri());
        if (isUsingFixedCamelContextName()) {
            detail.getJobDataMap().put(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME, getCamelContext().getName());
        } else {
            // must use management name as it should be unique in the same JVM
            detail.getJobDataMap().put(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME, QuartzHelper.getQuartzContextName(getCamelContext()));
        }
        if (detail.getJobClass() == null) {
            detail.setJobClass(isStateful() ? StatefulCamelJob.class : CamelJob.class);
        }
        if (detail.getName() == null) {
            detail.setName(getJobName());
        }
        getComponent().addJob(detail, trigger);
    }

    public void pauseTrigger(final Trigger trigger) throws SchedulerException {
        getComponent().pauseJob(trigger);
    }

    public void deleteTrigger(final Trigger trigger) throws SchedulerException {
        getComponent().deleteJob(trigger.getName(), trigger.getGroup());
    }

    /**
     * This method is invoked when a Quartz job is fired.
     *
     * @param jobExecutionContext the Quartz Job context
     */
    public void onJobExecute(final JobExecutionContext jobExecutionContext) throws JobExecutionException {
        boolean run = true;
        LoadBalancer balancer = getLoadBalancer();
        if (balancer instanceof ServiceSupport) {
            run = ((ServiceSupport) balancer).isRunAllowed();
        }

        if (!run) {
            // quartz scheduler could potential trigger during a route has been shutdown
            LOG.warn("Cannot execute Quartz Job with context: " + jobExecutionContext + " because processor is not started: " + balancer);
            return;
        }

        LOG.debug("Firing Quartz Job with context: {}", jobExecutionContext);
        Exchange exchange = createExchange(jobExecutionContext);
        try {
            balancer.process(exchange);

            if (exchange.getException() != null) {
                // propagate the exception back to Quartz
                throw new JobExecutionException(exchange.getException());
            }
        } catch (Exception e) {
            // log the error
            LOG.error(CamelExchangeException.createExceptionMessage("Error processing exchange", exchange, e));

            // and rethrow to let quartz handle it
            if (e instanceof JobExecutionException) {
                throw (JobExecutionException) e;
            }
            throw new JobExecutionException(e);
        }
    }

    public Exchange createExchange(final JobExecutionContext jobExecutionContext) {
        Exchange exchange = createExchange();
        exchange.setIn(new QuartzMessage(exchange, jobExecutionContext));
        return exchange;
    }

    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("You cannot send messages to this endpoint");
    }

    public QuartzConsumer createConsumer(Processor processor) throws Exception {
        QuartzConsumer answer = new QuartzConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    protected String createEndpointUri() {
        return "quartz://" + getTrigger().getGroup() + "/" + getTrigger().getName();
    }

    protected String getJobName() {
        return getJobDetail().getName();
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public QuartzComponent getComponent() {
        return (QuartzComponent) super.getComponent();
    }

    public boolean isSingleton() {
        return true;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getTimerName() {
        return timerName;
    }

    public void setTimerName(String timerName) {
        this.timerName = timerName;
    }

    public void setLoadBalancer(final LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public JobDetail getJobDetail() {
        return jobDetail;
    }

    public void setJobDetail(final JobDetail jobDetail) {
        this.jobDetail = jobDetail;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setTrigger(final Trigger trigger) {
        this.trigger = trigger;
    }

    public boolean isStateful() {
        return this.stateful;
    }

    public void setStateful(final boolean stateful) {
        this.stateful = stateful;
    }

    public boolean isDeleteJob() {
        return deleteJob;
    }

    public void setDeleteJob(boolean deleteJob) {
        this.deleteJob = deleteJob;
    }

    public boolean isPauseJob() {
        return pauseJob;
    }

    public void setPauseJob(boolean pauseJob) {
        this.pauseJob = pauseJob;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    public boolean isUsingFixedCamelContextName() {
        return usingFixedCamelContextName;
    }

    public void setUsingFixedCamelContextName(boolean usingFixedCamelContextName) {
        this.usingFixedCamelContextName = usingFixedCamelContextName;
    }

    public synchronized void consumerStarted(final QuartzConsumer consumer) throws SchedulerException {
        ObjectHelper.notNull(trigger, "trigger");
        LOG.debug("Adding consumer {}", consumer.getProcessor());
        getLoadBalancer().addProcessor(consumer.getProcessor());

        // if we have not yet added our default trigger, then lets do it
        if (!started) {
            addTrigger(getTrigger(), getJobDetail());
            started = true;
        }
    }

    public synchronized void consumerStopped(final QuartzConsumer consumer) throws SchedulerException {
        ObjectHelper.notNull(trigger, "trigger");
        if (started) {
            pauseTrigger(getTrigger());
            started = false;
        }

        LOG.debug("Removing consumer {}", consumer.getProcessor());
        getLoadBalancer().removeProcessor(consumer.getProcessor());
    }

    protected LoadBalancer createLoadBalancer() {
        return new RoundRobinLoadBalancer();
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(getComponent(), "QuartzComponent", this);

        if (loadBalancer == null) {
            loadBalancer = createLoadBalancer();
        }

        ServiceHelper.startService(loadBalancer);

        if (isDeleteJob() && isPauseJob()) {
            throw new IllegalArgumentException("Cannot have both options deleteJob and pauseJob enabled");
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(loadBalancer);
    }

    @Override
    protected void doShutdown() throws Exception {
        ObjectHelper.notNull(trigger, "trigger");
        if (isDeleteJob()) {
            deleteTrigger(getTrigger());
        } else if (isPauseJob()) {
            pauseTrigger(getTrigger());
        }
    }
}
