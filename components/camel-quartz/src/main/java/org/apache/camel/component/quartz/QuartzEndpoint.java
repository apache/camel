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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ShutdownableService;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

/**
 * A <a href="http://activemq.apache.org/quartz.html">Quartz Endpoint</a>
 *
 * @version $Revision:520964 $
 */
public class QuartzEndpoint extends DefaultEndpoint implements ShutdownableService {
    private static final transient Log LOG = LogFactory.getLog(QuartzEndpoint.class);

    private LoadBalancer loadBalancer;
    private Trigger trigger;
    private JobDetail jobDetail;
    private volatile boolean started;
    private volatile boolean stateful;

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
        detail.getJobDataMap().put(QuartzConstants.QUARTZ_CAMEL_CONTEXT_NAME, getCamelContext().getName());
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

        if (LOG.isDebugEnabled()) {
            LOG.debug("Firing Quartz Job with context: " + jobExecutionContext);
        }
        Exchange exchange = createExchange(jobExecutionContext);
        try {
            balancer.process(exchange);

            if (exchange.getException() != null) {
                // propagate the exception back to Quartz
                throw new JobExecutionException(exchange.getException());
            }
        } catch (Exception e) {
            // log the error
            LOG.error(ExchangeHelper.createExceptionMessage("Error processing exchange", exchange, e));

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
        return new QuartzConsumer(this, processor);
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
        if (loadBalancer == null) {
            loadBalancer = createLoadBalancer();
        }
        return loadBalancer;
    }

    public void setLoadBalancer(final LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public JobDetail getJobDetail() {
        if (jobDetail == null) {
            jobDetail = createJobDetail();
        }
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

    // Implementation methods
    // -------------------------------------------------------------------------

    public synchronized void consumerStarted(final QuartzConsumer consumer) throws SchedulerException {
        ObjectHelper.notNull(trigger, "trigger");
        if (LOG.isDebugEnabled()) {
            LOG.debug("Adding consumer " + consumer.getProcessor());
        }
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

        if (LOG.isDebugEnabled()) {
            LOG.debug("Removing consumer " + consumer.getProcessor());
        }
        getLoadBalancer().removeProcessor(consumer.getProcessor());
    }

    protected LoadBalancer createLoadBalancer() {
        return new RoundRobinLoadBalancer();
    }

    protected JobDetail createJobDetail() {
        return new JobDetail();
    }

    public void start() throws Exception {
        ObjectHelper.notNull(getComponent(), "QuartzComponent", this);
        ServiceHelper.startService(loadBalancer);
    }

    public void stop() throws Exception {
        ServiceHelper.stopService(loadBalancer);
    }

    public void shutdown() throws Exception {
        ObjectHelper.notNull(trigger, "trigger");
        deleteTrigger(getTrigger());
    }
}
