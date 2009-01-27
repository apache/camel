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
import java.util.Map;
import java.util.Set;

import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

/**
 * A <a href="http://activemq.apache.org/quartz.html">Quartz Endpoint</a>
 *
 * @version $Revision:520964 $
 */
public class QuartzEndpoint extends DefaultEndpoint<QuartzExchange> {
    public static final String ENDPOINT_KEY = "org.apache.camel.quartz";
    public static final String CONTEXT_KEY = "org.apache.camel.CamelContext";

    private static final transient Log LOG = LogFactory.getLog(QuartzEndpoint.class);
    private Scheduler scheduler;
    private LoadBalancer loadBalancer;
    private Trigger trigger;
    private JobDetail jobDetail;
    private boolean started;
    private boolean stateful;

    public QuartzEndpoint(final String endpointUri, final QuartzComponent component, final Scheduler scheduler) {
        super(endpointUri, component);
        this.scheduler = scheduler;
    }

    public QuartzEndpoint(final String endpointUri, final Scheduler scheduler) {
        super(endpointUri);
        this.scheduler = scheduler;
    }

    public void addTriggers(final Map<Trigger, JobDetail> triggerMap) throws SchedulerException {
        if (triggerMap != null) {
            Set<Map.Entry<Trigger, JobDetail>> entries = triggerMap.entrySet();
            for (Map.Entry<Trigger, JobDetail> entry : entries) {
                Trigger key = entry.getKey();
                JobDetail value = entry.getValue();
                ObjectHelper.notNull(key, "key");
                ObjectHelper.notNull(value, "value");

                addTrigger(key, value);
            }
        }
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
        if (isStateful()) {
            detail.getJobDataMap().put(ENDPOINT_KEY, getEndpointUri());
        } else {
            detail.getJobDataMap().put(ENDPOINT_KEY, this);
        }
        if (null == detail.getJobClass()) {
            if (isStateful()) {
                detail.setJobClass(StatefulCamelJob.class);
            } else {
                detail.setJobClass(CamelJob.class);
            }
        }
        if (detail.getName() == null) {
            detail.setName(getEndpointUri());
        }
        getScheduler().scheduleJob(detail, trigger);
    }

    public void removeTrigger(final Trigger trigger, final JobDetail jobDetail) throws SchedulerException {
        getScheduler().unscheduleJob(trigger.getName(), trigger.getGroup());
    }

    /**
     * This method is invoked when a Quartz job is fired.
     *
     * @param jobExecutionContext the Quartz Job context
     */
    public void onJobExecute(final JobExecutionContext jobExecutionContext) throws JobExecutionException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Firing Quartz Job with context: " + jobExecutionContext);
        }
        QuartzExchange exchange = createExchange(jobExecutionContext);
        try {
            getLoadBalancer().process(exchange);
        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new JobExecutionException(e);
        }
    }

    @Override
    public QuartzExchange createExchange(final ExchangePattern pattern) {
        return new QuartzExchange(getCamelContext(), pattern, null);
    }

    public QuartzExchange createExchange(final JobExecutionContext jobExecutionContext) {
        return new QuartzExchange(getCamelContext(), getExchangePattern(), jobExecutionContext);
    }

    public Producer<QuartzExchange> createProducer() throws Exception {
        throw new UnsupportedOperationException("You cannot send messages to this endpoint");
    }

    public QuartzConsumer createConsumer(final Processor processor) throws Exception {
        return new QuartzConsumer(this, processor);
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public QuartzComponent getComponent() {
        return (QuartzComponent)super.getComponent();
    }

    public boolean isSingleton() {
        return true;
    }

    public Scheduler getScheduler() {
        return scheduler;
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
        if (trigger == null) {
            trigger = createTrigger();
        }
        return trigger;
    }

    public void setTrigger(final Trigger trigger) {
        this.trigger = trigger;
    }

    /**
     * @return the stateful
     */
    public boolean isStateful() {
        return this.stateful;
    }

    /**
     * @param stateful the stateful to set
     */
    public void setStateful(final boolean stateful) {
        this.stateful = stateful;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    public synchronized void consumerStarted(final QuartzConsumer consumer) throws SchedulerException {
        getLoadBalancer().addProcessor(consumer.getProcessor());

        // if we have not yet added our default trigger, then lets do it
        if (!started) {
            addTrigger(getTrigger(), getJobDetail());
            started = true;
        }
    }

    public synchronized void consumerStopped(final QuartzConsumer consumer) throws SchedulerException {
        getLoadBalancer().removeProcessor(consumer.getProcessor());
        if (getLoadBalancer().getProcessors().isEmpty() && started) {
            removeTrigger(getTrigger(), getJobDetail());
            started = false;
        }
    }

    protected LoadBalancer createLoadBalancer() {
        return new RoundRobinLoadBalancer();
    }

    protected JobDetail createJobDetail() {
        return new JobDetail();
    }

    protected Trigger createTrigger() {
        return new SimpleTrigger();
    }
}
