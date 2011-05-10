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
package org.apache.camel.impl;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.SuspendableService;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class for any consumer which is polling based
 * 
 * @version 
 */
public abstract class ScheduledPollConsumer extends DefaultConsumer implements Runnable, SuspendableService {
    private static final transient Logger LOG = LoggerFactory.getLogger(ScheduledPollConsumer.class);

    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> future;

    // if adding more options then align with ScheduledPollEndpoint#configureScheduledPollConsumerProperties
    private long initialDelay = 1000;
    private long delay = 500;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private boolean useFixedDelay;
    private PollingConsumerPollStrategy pollStrategy = new DefaultPollingConsumerPollStrategy();

    public ScheduledPollConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);

        // we only need one thread in the pool to schedule this task
        this.executor = endpoint.getCamelContext().getExecutorServiceStrategy()
                            .newScheduledThreadPool(this, endpoint.getEndpointUri(), 1);
        ObjectHelper.notNull(executor, "executor");
    }

    public ScheduledPollConsumer(Endpoint endpoint, Processor processor, ScheduledExecutorService executor) {
        super(endpoint, processor);
        this.executor = executor;
        ObjectHelper.notNull(executor, "executor");
    }

    /**
     * Invoked whenever we should be polled
     */
    public void run() {
        if (isSuspended()) {
            LOG.trace("Cannot start to poll: {} as its suspended", this.getEndpoint());
            return;
        }

        int retryCounter = -1;
        boolean done = false;

        while (!done) {
            try {
                // eager assume we are done
                done = true;
                if (isPollAllowed()) {

                    if (retryCounter == -1) {
                        LOG.trace("Starting to poll: {}", this.getEndpoint());
                    } else {
                        LOG.debug("Retrying attempt {} to poll: {}", retryCounter, this.getEndpoint());
                    }

                    boolean begin = pollStrategy.begin(this, getEndpoint());
                    if (begin) {
                        retryCounter++;
                        int polledMessages = poll();
                        pollStrategy.commit(this, getEndpoint(), polledMessages);
                    } else {
                        LOG.debug("Cannot begin polling as pollStrategy returned false: {}", pollStrategy);
                    }
                }

                LOG.trace("Finished polling: {}", this.getEndpoint());
            } catch (Exception e) {
                try {
                    boolean retry = pollStrategy.rollback(this, getEndpoint(), retryCounter, e);
                    if (retry) {
                        done = false;
                    }
                } catch (Throwable t) {
                    // catch throwable to not let the thread die
                    getExceptionHandler().handleException("Consumer " + this +  " failed polling endpoint: " + getEndpoint().getEndpointUri()
                            + ". Will try again at next poll", t);
                    // we are done due this fatal error
                    done = true;
                }
            } catch (Throwable t) {
                // catch throwable to not let the thread die
                getExceptionHandler().handleException("Consumer " + this +  " failed polling endpoint: " + getEndpoint().getEndpointUri()
                        + ". Will try again at next poll", t);
                // we are done due this fatal error
                done = true;
            }
        }

        // avoid this thread to throw exceptions because the thread pool wont re-schedule a new thread
    }

    // Properties
    // -------------------------------------------------------------------------

    protected boolean isPollAllowed() {
        return isRunAllowed() && !isSuspended();
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit to use.
     * <p/>
     * Notice that both {@link #getDelay()} and {@link #getInitialDelay()} are using
     * the same time unit. So if you change this value, then take into account that the
     * default value of {@link #getInitialDelay()} is 1000. So you may to adjust this value accordingly.
     *
     * @param timeUnit the time unit.
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public boolean isUseFixedDelay() {
        return useFixedDelay;
    }

    public void setUseFixedDelay(boolean useFixedDelay) {
        this.useFixedDelay = useFixedDelay;
    }

    public PollingConsumerPollStrategy getPollStrategy() {
        return pollStrategy;
    }

    public void setPollStrategy(PollingConsumerPollStrategy pollStrategy) {
        this.pollStrategy = pollStrategy;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * The polling method which is invoked periodically to poll this consumer
     *
     * @return number of messages polled, will be <tt>0</tt> if no message was polled at all.
     * @throws Exception can be thrown if an exception occurred during polling
     */
    protected abstract int poll() throws Exception;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (isUseFixedDelay()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scheduling poll (fixed delay) with initialDelay: {}, delay: {} ({}) for: {}",
                        new Object[]{getInitialDelay(), getDelay(), getTimeUnit().name().toLowerCase(), getEndpoint()});
            }
            future = executor.scheduleWithFixedDelay(this, getInitialDelay(), getDelay(), getTimeUnit());
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Scheduling poll (fixed rate) with initialDelay: {}, delay: {} ({}) for: {}",
                        new Object[]{getInitialDelay(), getDelay(), getTimeUnit().name().toLowerCase(), getEndpoint()});
            }
            future = executor.scheduleAtFixedRate(this, getInitialDelay(), getDelay(), getTimeUnit());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(false);
        }
        super.doStop();
    }

    @Override
    protected void doSuspend() throws Exception {
        // dont stop/cancel the future task since we just check in the run method
    }
}
