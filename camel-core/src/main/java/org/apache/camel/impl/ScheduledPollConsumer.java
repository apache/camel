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

import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.PollingConsumerPollingStrategy;
import org.apache.camel.Processor;
import org.apache.camel.SuspendableService;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class for any consumer which is polling based
 * 
 * @version 
 */
public abstract class ScheduledPollConsumer extends DefaultConsumer implements Runnable, SuspendableService, PollingConsumerPollingStrategy {
    private static final transient Logger LOG = LoggerFactory.getLogger(ScheduledPollConsumer.class);

    private ScheduledExecutorService scheduledExecutorService;
    private boolean shutdownExecutor;
    private volatile ScheduledFuture<?> future;

    // if adding more options then align with ScheduledPollEndpoint#configureScheduledPollConsumerProperties
    @UriParam
    private boolean startScheduler = true;
    @UriParam
    private long initialDelay = 1000;
    @UriParam
    private long delay = 500;
    @UriParam
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    @UriParam
    private boolean useFixedDelay = true;
    @UriParam
    private PollingConsumerPollStrategy pollStrategy = new DefaultPollingConsumerPollStrategy();
    @UriParam
    private LoggingLevel runLoggingLevel = LoggingLevel.TRACE;
    @UriParam
    private boolean sendEmptyMessageWhenIdle;
    @UriParam
    private boolean greedy;
    private volatile boolean polling;

    public ScheduledPollConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    public ScheduledPollConsumer(Endpoint endpoint, Processor processor, ScheduledExecutorService scheduledExecutorService) {
        super(endpoint, processor);
        // we have been given an existing thread pool, so we should not manage its lifecycle
        // so we should keep shutdownExecutor as false
        this.scheduledExecutorService = scheduledExecutorService;
        ObjectHelper.notNull(scheduledExecutorService, "scheduledExecutorService");
    }

    /**
     * Invoked whenever we should be polled
     */
    public void run() {
        // avoid this thread to throw exceptions because the thread pool wont re-schedule a new thread
        try {
            // log starting
            if (LoggingLevel.ERROR == runLoggingLevel) {
                LOG.error("Scheduled task started on:   {}", this.getEndpoint());
            } else if (LoggingLevel.WARN == runLoggingLevel) {
                LOG.warn("Scheduled task started on:   {}", this.getEndpoint());
            } else if (LoggingLevel.INFO == runLoggingLevel) {
                LOG.info("Scheduled task started on:   {}", this.getEndpoint());
            } else if (LoggingLevel.DEBUG == runLoggingLevel) {
                LOG.debug("Scheduled task started on:   {}", this.getEndpoint());
            } else {
                LOG.trace("Scheduled task started on:   {}", this.getEndpoint());
            }

            // execute scheduled task
            doRun();

            // log completed
            if (LoggingLevel.ERROR == runLoggingLevel) {
                LOG.error("Scheduled task completed on: {}", this.getEndpoint());
            } else if (LoggingLevel.WARN == runLoggingLevel) {
                LOG.warn("Scheduled task completed on: {}", this.getEndpoint());
            } else if (LoggingLevel.INFO == runLoggingLevel) {
                LOG.info("Scheduled task completed on: {}", this.getEndpoint());
            } else if (LoggingLevel.DEBUG == runLoggingLevel) {
                LOG.debug("Scheduled task completed on: {}", this.getEndpoint());
            } else {
                LOG.trace("Scheduled task completed on: {}", this.getEndpoint());
            }

        } catch (Error e) {
            // must catch Error, to ensure the task is re-scheduled
            LOG.error("Error occurred during running scheduled task on: " + this.getEndpoint() + ", due: " + e.getMessage(), e);
        }
    }

    private void doRun() {
        if (isSuspended()) {
            LOG.trace("Cannot start to poll: {} as its suspended", this.getEndpoint());
            return;
        }

        int retryCounter = -1;
        boolean done = false;
        Throwable cause = null;

        while (!done) {
            try {
                cause = null;
                // eager assume we are done
                done = true;
                if (isPollAllowed()) {

                    if (retryCounter == -1) {
                        LOG.trace("Starting to poll: {}", this.getEndpoint());
                    } else {
                        LOG.debug("Retrying attempt {} to poll: {}", retryCounter, this.getEndpoint());
                    }

                    // mark we are polling which should also include the begin/poll/commit
                    polling = true;
                    try {
                        boolean begin = pollStrategy.begin(this, getEndpoint());
                        if (begin) {
                            retryCounter++;
                            int polledMessages = poll();

                            if (polledMessages == 0 && isSendEmptyMessageWhenIdle()) {
                                // send an "empty" exchange
                                processEmptyMessage();
                            }

                            pollStrategy.commit(this, getEndpoint(), polledMessages);

                            if (polledMessages > 0 && isGreedy()) {
                                done = false;
                                retryCounter = -1;
                                LOG.trace("Greedy polling after processing {} messages", polledMessages);
                            }
                        } else {
                            LOG.debug("Cannot begin polling as pollStrategy returned false: {}", pollStrategy);
                        }
                    } finally {
                        polling = false;
                    }
                }

                LOG.trace("Finished polling: {}", this.getEndpoint());
            } catch (Exception e) {
                try {
                    boolean retry = pollStrategy.rollback(this, getEndpoint(), retryCounter, e);
                    if (retry) {
                        // do not set cause as we retry
                        done = false;
                    } else {
                        cause = e;
                        done = true;
                    }
                } catch (Throwable t) {
                    cause = t;
                    done = true;
                }
            } catch (Throwable t) {
                cause = t;
                done = true;
            }

            if (cause != null && isRunAllowed()) {
                // let exception handler deal with the caused exception
                // but suppress this during shutdown as the logs may get flooded with exceptions during shutdown/forced shutdown
                try {
                    getExceptionHandler().handleException("Consumer " + this +  " failed polling endpoint: " + getEndpoint()
                            + ". Will try again at next poll", cause);
                } catch (Throwable e) {
                    LOG.warn("Error handling exception. This exception will be ignored.", e);
                }
                cause = null;
            }
        }

        // avoid this thread to throw exceptions because the thread pool wont re-schedule a new thread
    }

    /**
     * No messages to poll so send an empty message instead.
     *
     * @throws Exception is thrown if error processing the empty message.
     */
    protected void processEmptyMessage() throws Exception {
        Exchange exchange = getEndpoint().createExchange();
        log.debug("Sending empty message as there were no messages from polling: {}", this.getEndpoint());
        getProcessor().process(exchange);
    }

    // Properties
    // -------------------------------------------------------------------------

    protected boolean isPollAllowed() {
        return isRunAllowed() && !isSuspended();
    }

    /**
     * Whether polling is currently in progress
     */
    protected boolean isPolling() {
        return polling;
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

    public LoggingLevel getRunLoggingLevel() {
        return runLoggingLevel;
    }

    public void setRunLoggingLevel(LoggingLevel runLoggingLevel) {
        this.runLoggingLevel = runLoggingLevel;
    }

    public PollingConsumerPollStrategy getPollStrategy() {
        return pollStrategy;
    }

    public void setPollStrategy(PollingConsumerPollStrategy pollStrategy) {
        this.pollStrategy = pollStrategy;
    }

    public boolean isStartScheduler() {
        return startScheduler;
    }

    /**
     * Sets whether the scheduler should be started when this consumer starts.
     * <p/>
     * This option is default true.
     *
     * @param startScheduler whether to start scheduler
     */
    public void setStartScheduler(boolean startScheduler) {
        this.startScheduler = startScheduler;
    }
    
    public void setSendEmptyMessageWhenIdle(boolean sendEmptyMessageWhenIdle) {
        this.sendEmptyMessageWhenIdle = sendEmptyMessageWhenIdle;
    }
    
    public boolean isSendEmptyMessageWhenIdle() {
        return sendEmptyMessageWhenIdle;
    }

    public boolean isGreedy() {
        return greedy;
    }

    /**
     * If greedy then a poll is executed immediate after a previous poll that polled 1 or more messages.
     */
    public void setGreedy(boolean greedy) {
        this.greedy = greedy;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    /**
     * Whether the scheduler has been started.
     * <p/>
     * The scheduler can be started with the {@link #startScheduler()} method.
     *
     * @return <tt>true</tt> if started, <tt>false</tt> if not.
     */
    public boolean isSchedulerStarted() {
        return future != null;
    }

    /**
     * Sets a custom shared {@link ScheduledExecutorService} to use as thread pool
     * <p/>
     * <b>Notice: </b> When using a custom thread pool, then the lifecycle of this thread
     * pool is not controlled by this consumer (eg this consumer will not start/stop the thread pool
     * when the consumer is started/stopped etc.)
     *
     * @param scheduledExecutorService the custom thread pool to use
     */
    public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
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

        // if no existing executor provided, then create a new thread pool ourselves
        if (scheduledExecutorService == null) {
            // we only need one thread in the pool to schedule this task
            this.scheduledExecutorService = getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newScheduledThreadPool(this, getEndpoint().getEndpointUri(), 1);
            // and we should shutdown the thread pool when no longer needed
            this.shutdownExecutor = true;
        }

        ObjectHelper.notNull(scheduledExecutorService, "scheduledExecutorService", this);
        ObjectHelper.notNull(pollStrategy, "pollStrategy", this);

        if (isStartScheduler()) {
            startScheduler();
        }
    }

    /**
     * Starts the scheduler.
     * <p/>
     * If the scheduler is already started, then this is a noop method call.
     */
    public void startScheduler() {
        // only schedule task if we have not already done that
        if (future == null) {
            if (isUseFixedDelay()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling poll (fixed delay) with initialDelay: {}, delay: {} ({}) for: {}",
                            new Object[]{getInitialDelay(), getDelay(), getTimeUnit().name().toLowerCase(Locale.ENGLISH), getEndpoint()});
                }
                future = scheduledExecutorService.scheduleWithFixedDelay(this, getInitialDelay(), getDelay(), getTimeUnit());
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling poll (fixed rate) with initialDelay: {}, delay: {} ({}) for: {}",
                            new Object[]{getInitialDelay(), getDelay(), getTimeUnit().name().toLowerCase(Locale.ENGLISH), getEndpoint()});
                }
                future = scheduledExecutorService.scheduleAtFixedRate(this, getInitialDelay(), getDelay(), getTimeUnit());
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            LOG.debug("This consumer is stopping, so cancelling scheduled task: " + future);
            future.cancel(false);
            future = null;
        }
        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        if (shutdownExecutor && scheduledExecutorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(scheduledExecutorService);
            scheduledExecutorService = null;
            future = null;
        }
        super.doShutdown();
    }

    @Override
    protected void doSuspend() throws Exception {
        // dont stop/cancel the future task since we just check in the run method
    }

    @Override
    public void onInit() throws Exception {
        // noop
    }

    @Override
    public long beforePoll(long timeout) throws Exception {
        LOG.trace("Before poll {}", getEndpoint());
        // resume or start our self
        if (!ServiceHelper.resumeService(this)) {
            ServiceHelper.startService(this);
        }

        // ensure at least timeout is as long as one poll delay
        return Math.max(timeout, getDelay());
    }

    @Override
    public void afterPoll() throws Exception {
        LOG.trace("After poll {}", getEndpoint());
        // suspend or stop our self
        if (!ServiceHelper.suspendService(this)) {
            ServiceHelper.stopService(this);
        }
    }

}
