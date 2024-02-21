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
package org.apache.camel.support;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.FailedToCreateConsumerException;
import org.apache.camel.LoggingLevel;
import org.apache.camel.PollingConsumerPollingStrategy;
import org.apache.camel.Processor;
import org.apache.camel.Suspendable;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckAware;
import org.apache.camel.spi.HttpResponseAware;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class for any consumer which is polling based
 */
public abstract class ScheduledPollConsumer extends DefaultConsumer
        implements Runnable, Suspendable, PollingConsumerPollingStrategy, HealthCheckAware {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledPollConsumer.class);

    private ScheduledPollConsumerScheduler scheduler;
    private ScheduledExecutorService scheduledExecutorService;

    // if adding more options then align with org.apache.camel.support.ScheduledPollEndpoint

    private boolean startScheduler = true;
    private long initialDelay = 1000;
    private long delay = 500;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private boolean useFixedDelay = true;
    private PollingConsumerPollStrategy pollStrategy = new DefaultPollingConsumerPollStrategy();
    private LoggingLevel runLoggingLevel = LoggingLevel.TRACE;
    private boolean sendEmptyMessageWhenIdle;
    private boolean greedy;
    private int backoffMultiplier;
    private int backoffIdleThreshold;
    private int backoffErrorThreshold;
    private long repeatCount;
    private Map<String, Object> schedulerProperties;

    // state during running
    private volatile boolean polling;
    private volatile int backoffCounter;
    private volatile long idleCounter;
    private volatile long errorCounter;
    private volatile long successCounter;
    private volatile Throwable lastError;
    private volatile Map<String, Object> lastErrorDetails;
    private final AtomicLong counter = new AtomicLong();
    private volatile boolean firstPollDone;
    private volatile boolean forceReady;

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
    @Override
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
            LOG.error("Error occurred during running scheduled task on: {}, due: {}."
                      + " This exception is ignored and the task will run again on next poll.",
                    this.getEndpoint(), e.getMessage(), e);
        }
    }

    private void doRun() {
        if (isSuspended()) {
            LOG.trace("Cannot start to poll: {} as its suspended", this.getEndpoint());
            return;
        }

        // should we backoff if its enabled, and either the idle or error counter is > the threshold
        if (backoffMultiplier > 0
                // either idle or error threshold could be not in use, so check for that and use MAX_VALUE if not in use
                && idleCounter >= (backoffIdleThreshold > 0 ? backoffIdleThreshold : Integer.MAX_VALUE)
                || errorCounter >= (backoffErrorThreshold > 0 ? backoffErrorThreshold : Integer.MAX_VALUE)) {
            if (backoffCounter++ < backoffMultiplier) {
                // yes we should backoff
                if (idleCounter > 0) {
                    LOG.debug("doRun() backoff due subsequent {} idles (backoff at {}/{})", idleCounter, backoffCounter,
                            backoffMultiplier);
                } else {
                    LOG.debug("doRun() backoff due subsequent {} errors (backoff at {}/{})", errorCounter, backoffCounter,
                            backoffMultiplier);
                }
                return;
            } else {
                // we are finished with backoff so reset counters
                idleCounter = 0;
                errorCounter = 0;
                backoffCounter = 0;
                successCounter = 0;
                LOG.trace("doRun() backoff finished, resetting counters.");
            }
        }

        long count = counter.incrementAndGet();
        boolean stopFire = repeatCount > 0 && count > repeatCount;
        if (stopFire) {
            LOG.debug("Cancelling {} scheduler as repeat count limit reached after {} counts.", getEndpoint(), repeatCount);
            scheduler.unscheduleTask();
            return;
        }

        int retryCounter = -1;
        boolean done = false;
        Throwable cause = null;
        int polledMessages = 0;

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
                            polledMessages = poll();
                            LOG.trace("Polled {} messages", polledMessages);

                            if (polledMessages == 0 && isSendEmptyMessageWhenIdle()) {
                                // send an "empty" exchange
                                processEmptyMessage();
                            }

                            pollStrategy.commit(this, getEndpoint(), polledMessages);

                            if (polledMessages > 0 && isGreedy()) {
                                done = false;
                                retryCounter = -1;
                                LOG.trace("Greedy polling after processing {} messages", polledMessages);

                                // clear any error that might be since we have successfully polled, otherwise readiness checks might believe the
                                // consumer to be unhealthy
                                errorCounter = 0;
                                lastError = null;
                                lastErrorDetails = null;

                                // setting firstPollDone to true if greedy polling is enabled
                                firstPollDone = true;
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
                } catch (Exception t) {
                    cause = t;
                    done = true;
                }
            }

            if (cause != null && isRunAllowed()) {
                // let exception handler deal with the caused exception
                // but suppress this during shutdown as the logs may get flooded with exceptions during shutdown/forced shutdown
                try {
                    getExceptionHandler().handleException("Failed polling endpoint: " + getEndpoint()
                                                          + ". Will try again at next poll",
                            cause);
                } catch (Exception e) {
                    LOG.warn("Error handling exception. This exception will be ignored.", e);
                }
            }
        }

        if (cause != null) {
            idleCounter = 0;
            successCounter = 0;
            errorCounter++;
            lastError = cause;
            // enrich last error with http response code if possible
            if (cause instanceof HttpResponseAware) {
                int code = ((HttpResponseAware) cause).getHttpResponseCode();
                if (code > 0) {
                    addLastErrorDetail(HealthCheck.HTTP_RESPONSE_CODE, code);
                }
            }
        } else {
            idleCounter = polledMessages == 0 ? ++idleCounter : 0;
            successCounter++;
            errorCounter = 0;
            lastError = null;
            lastErrorDetails = null;
        }

        // now first pool is done after the poll is complete
        firstPollDone = true;

        LOG.trace("doRun() done with idleCounter={}, successCounter={}, errorCounter={}", idleCounter, successCounter,
                errorCounter);

        // avoid this thread to throw exceptions because the thread pool wont re-schedule a new thread
    }

    /**
     * No messages to poll so send an empty message instead.
     *
     * @throws Exception is thrown if error processing the empty message.
     */
    protected void processEmptyMessage() throws Exception {
        Exchange exchange = getEndpoint().createExchange();
        LOG.debug("Sending empty message as there were no messages from polling: {}", this.getEndpoint());
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
    public boolean isPolling() {
        return polling;
    }

    public ScheduledPollConsumerScheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(ScheduledPollConsumerScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Map<String, Object> getSchedulerProperties() {
        return schedulerProperties;
    }

    public void setSchedulerProperties(Map<String, Object> schedulerProperties) {
        this.schedulerProperties = schedulerProperties;
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

    public void setGreedy(boolean greedy) {
        this.greedy = greedy;
    }

    public int getBackoffCounter() {
        return backoffCounter;
    }

    public int getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public void setBackoffMultiplier(int backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public int getBackoffIdleThreshold() {
        return backoffIdleThreshold;
    }

    public void setBackoffIdleThreshold(int backoffIdleThreshold) {
        this.backoffIdleThreshold = backoffIdleThreshold;
    }

    public int getBackoffErrorThreshold() {
        return backoffErrorThreshold;
    }

    public void setBackoffErrorThreshold(int backoffErrorThreshold) {
        this.backoffErrorThreshold = backoffErrorThreshold;
    }

    public long getRepeatCount() {
        return repeatCount;
    }

    public void setRepeatCount(long repeatCount) {
        this.repeatCount = repeatCount;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    public boolean isSchedulerStarted() {
        return scheduler.isSchedulerStarted();
    }

    public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }

    /**
     * Gets the error counter. If the counter is > 0 that means the consumer failed polling for the last N number of
     * times. When the consumer is successfully again, then the error counter resets to zero.
     *
     * @see #getSuccessCounter()
     */
    public long getErrorCounter() {
        return errorCounter;
    }

    /**
     * Gets the success counter. If the success is > 0 that means the consumer succeeded polling for the last N number
     * of times. When the consumer is failing again, then the success counter resets to zero.
     *
     * @see #getErrorCounter()
     */
    public long getSuccessCounter() {
        return successCounter;
    }

    /**
     * Gets the total number of polls run.
     */
    public long getCounter() {
        return counter.get();
    }

    /**
     * Whether a first pool attempt has been done (also if the consumer has been restarted).
     */
    public boolean isFirstPollDone() {
        return firstPollDone;
    }

    /**
     * Whether the consumer is ready and has established connection to its target system, or first poll has been
     * completed successfully.
     *
     * The health-check is using this information to know when the consumer is ready for readiness checks.
     */
    public boolean isConsumerReady() {
        // we regard the consumer as ready if it was explicit forced to be ready (component specific)
        // or that it has completed its first poll without an exception was thrown
        // during connecting to target system and accepting data
        return forceReady || firstPollDone;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * Forces the consumer to be marked as ready. This can be used by components that need to mark this sooner than
     * usual (default marked as ready after first poll is done). This allows health-checks to be ready before an entire
     * poll is completed.
     *
     * This is for example needed by the FTP component as polling a large file can take long time, causing a
     * health-check to not be ready within reasonable time.
     */
    protected void forceConsumerAsReady() {
        forceReady = true;
    }

    /**
     * Gets the last caused error (exception) for the last poll that failed. When the consumer is successfully again,
     * then the error resets to null.
     */
    protected Throwable getLastError() {
        return lastError;
    }

    /**
     * Gets the last caused error (exception) details for the last poll that failed. When the consumer is successfully
     * again, then the error resets to null.
     *
     * Some consumers can provide additional error details here, besides the caused exception. For example if the
     * consumer uses HTTP then the {@link org.apache.camel.health.HealthCheck#HTTP_RESPONSE_CODE} can be included.
     *
     * @return error details, or null if no details exists.
     */
    protected Map<String, Object> getLastErrorDetails() {
        return lastErrorDetails;
    }

    /**
     * Adds a detail to the last caused error (exception) for the last poll that failed. When the consumer is
     * successfully again, then the error resets to null.
     *
     * Some consumers can provide additional error details here, besides the caused exception. For example if the
     * consumer uses HTTP then the {@link org.apache.camel.health.HealthCheck#HTTP_RESPONSE_CODE} can be included.
     *
     * @param key   the key (see {@link org.apache.camel.health.HealthCheck})
     * @param value the value
     */
    protected void addLastErrorDetail(String key, Object value) {
        if (lastErrorDetails == null) {
            lastErrorDetails = new HashMap<>();
        }
        if (lastErrorDetails != null) {
            lastErrorDetails.put(key, value);
        }
    }

    /**
     * The polling method which is invoked periodically to poll this consumer
     *
     * @return           number of messages polled, will be <tt>0</tt> if no message was polled at all.
     * @throws Exception can be thrown if an exception occurred during polling
     */
    protected abstract int poll() throws Exception;

    @Override
    protected void doBuild() throws Exception {
        if (getHealthCheck() == null) {
            String id = "consumer:" + getRouteId();
            ScheduledPollConsumerHealthCheck hc = new ScheduledPollConsumerHealthCheck(this, id);
            // is there a custom initial state the consumer must use
            HealthCheck.State initialState = initialHealthCheckState();
            if (initialState != null) {
                hc.setInitialState(initialState);
            }
            setHealthCheck(hc);
        }
        super.doBuild();
    }

    /**
     * Used to allow special consumers to override the initial state of the health check (readiness check) during
     * startup.
     *
     * Consumers that are internal only such as camel-scheduler uses UP as initial state because the scheduler may be
     * configured to run only very in-frequently and therefore the overall health-check state would be affected and seen
     * as DOWN.
     *
     * @return null to use the initial state configured, otherwise force using the returned state.
     */
    protected HealthCheck.State initialHealthCheckState() {
        return null;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        Component component = getEndpoint().getComponent();
        if (component instanceof HealthCheckComponent hcc) {
            getHealthCheck().setEnabled(hcc.isHealthCheckConsumerEnabled());
        }

        // validate that if backoff multiplier is in use, the threshold values is set correctly
        if (backoffMultiplier > 0) {
            if (backoffIdleThreshold <= 0 && backoffErrorThreshold <= 0) {
                throw new IllegalArgumentException(
                        "backoffIdleThreshold and/or backoffErrorThreshold must be configured to a positive value when using backoffMultiplier");
            }
            LOG.debug("Using backoff[multiplier={}, idleThreshold={}, errorThreshold={}] on {}", backoffMultiplier,
                    backoffIdleThreshold, backoffErrorThreshold, getEndpoint());
        }

        ObjectHelper.notNull(pollStrategy, "pollStrategy", this);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        boolean newScheduler = false;
        if (scheduler == null) {
            DefaultScheduledPollConsumerScheduler scheduler
                    = new DefaultScheduledPollConsumerScheduler(scheduledExecutorService);
            scheduler.setDelay(delay);
            scheduler.setInitialDelay(initialDelay);
            scheduler.setTimeUnit(timeUnit);
            scheduler.setUseFixedDelay(useFixedDelay);
            this.scheduler = scheduler;
            newScheduler = true;
        }
        ObjectHelper.notNull(scheduler, "scheduler", this);
        scheduler.setCamelContext(getEndpoint().getCamelContext());

        // configure scheduler with options from this consumer
        if (schedulerProperties != null && !schedulerProperties.isEmpty()) {
            // need to use a copy in case the consumer is restarted so we keep the properties
            Map<String, Object> copy = new LinkedHashMap<>(schedulerProperties);
            PropertyBindingSupport.build().bind(getEndpoint().getCamelContext(), scheduler, copy);
            // special for trigger and job parameters
            Map<String, Object> triggerParameters = PropertiesHelper.extractProperties(copy, "trigger.");
            Map<String, Object> jobParameters = PropertiesHelper.extractProperties(copy, "job.");
            PropertyBindingSupport.build().bind(getEndpoint().getCamelContext(), scheduler, "triggerParameters",
                    triggerParameters);
            PropertyBindingSupport.build().bind(getEndpoint().getCamelContext(), scheduler, "jobParameters", jobParameters);
            if (!copy.isEmpty()) {
                throw new FailedToCreateConsumerException(
                        getEndpoint(), "There are " + copy.size()
                                       + " scheduler parameters that couldn't be set on the endpoint."
                                       + " Check the uri if the parameters are spelt correctly and that they are properties of the endpoint."
                                       + " Unknown parameters=[" + copy + "]");
            }
        }
        afterConfigureScheduler(scheduler, newScheduler);

        scheduler.onInit(this);

        if (scheduler != null) {
            scheduler.scheduleTask(this);
            ServiceHelper.startService(scheduler);

            if (isStartScheduler()) {
                startScheduler();
            }
        }
    }

    /**
     * After the scheduler has been configured
     *
     * @param scheduler    the scheduler
     * @param newScheduler true if this consumer created a new scheduler, or false if an existing (shared) scheduler is
     *                     being used
     */
    protected void afterConfigureScheduler(ScheduledPollConsumerScheduler scheduler, boolean newScheduler) {
        // noop
    }

    /**
     * Starts the scheduler.
     * <p/>
     * If the scheduler is already started, then this is a noop method call.
     */
    public void startScheduler() {
        scheduler.startScheduler();
    }

    @Override
    protected void doStop() throws Exception {
        if (scheduler != null) {
            scheduler.unscheduleTask();
            ServiceHelper.stopAndShutdownServices(scheduler);
        }

        // clear counters
        backoffCounter = 0;
        idleCounter = 0;
        errorCounter = 0;
        successCounter = 0;
        counter.set(0);
        // clear ready state
        firstPollDone = false;
        forceReady = false;

        super.doStop();
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(scheduler);
        super.doShutdown();
    }

    @Override
    protected void doSuspend() throws Exception {
        // dont stop/cancel the future task since we just check in the run method
    }

    @Override
    public void onInit() throws Exception {
        // make sure the scheduler is starting
        startScheduler = true;
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
