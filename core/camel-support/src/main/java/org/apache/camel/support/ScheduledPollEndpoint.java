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

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.PollingConsumerPollStrategy;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.PropertiesHelper;

/**
 * A base class for {@link org.apache.camel.Endpoint} which creates a {@link ScheduledPollConsumer}
 */
public abstract class ScheduledPollEndpoint extends DefaultEndpoint {

    private static final String SPRING_SCHEDULER = "org.apache.camel.spring.pollingconsumer.SpringScheduledPollConsumerScheduler";
    private static final String QUARTZ_SCHEDULER = "org.apache.camel.pollconsumer.quartz.QuartzScheduledPollConsumerScheduler";

    private transient ScheduledPollConsumerScheduler consumerScheduler;

    // if adding more options then align with org.apache.camel.support.ScheduledPollConsumer
    @UriParam(defaultValue = "true", label = "consumer,scheduler",
            description = "Whether the scheduler should be auto started.")
    private boolean startScheduler = true;
    @UriParam(defaultValue = "1000", label = "consumer,scheduler",
            description = "Milliseconds before the first poll starts.")
    private long initialDelay = 1000;
    @UriParam(defaultValue = "500", label = "consumer,scheduler",
            description = "Milliseconds before the next poll.")
    private long delay = 500;
    @UriParam(defaultValue = "MILLISECONDS", label = "consumer,scheduler",
            description = "Time unit for initialDelay and delay options.")
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    @UriParam(defaultValue = "true", label = "consumer,scheduler",
            description = "Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.")
    private boolean useFixedDelay = true;
    @UriParam(label = "consumer,advanced",
            description = "A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing you to provide your custom implementation"
                    + " to control error handling usually occurred during the poll operation before an Exchange have been created and being routed in Camel.")
    private PollingConsumerPollStrategy pollStrategy = new DefaultPollingConsumerPollStrategy();
    @UriParam(defaultValue = "TRACE", label = "consumer,scheduler",
            description = "The consumer logs a start/complete log line when it polls. This option allows you to configure the logging level for that.")
    private LoggingLevel runLoggingLevel = LoggingLevel.TRACE;
    @UriParam(label = "consumer",
            description = "If the polling consumer did not poll any files, you can enable this option to send an empty message (no body) instead.")
    private boolean sendEmptyMessageWhenIdle;
    @UriParam(label = "consumer,scheduler",
            description = "If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the previous run polled 1 or more messages.")
    private boolean greedy;
    @UriParam(enums = "none,spring,quartz",
            defaultValue = "none", label = "consumer,scheduler", description = "To use a cron scheduler from either camel-spring or camel-quartz component")
    private String scheduler = "none";
    @UriParam(prefix = "scheduler.", multiValue = true, label = "consumer,scheduler",
            description = "To configure additional properties when using a custom scheduler or any of the Quartz, Spring based scheduler.")
    private Map<String, Object> schedulerProperties;
    @UriParam(label = "consumer,scheduler",
            description = "Allows for configuring a custom/shared thread pool to use for the consumer. By default each consumer has its own single threaded thread pool.")
    private ScheduledExecutorService scheduledExecutorService;
    @UriParam(label = "consumer,scheduler",
            description = "To let the scheduled polling consumer backoff if there has been a number of subsequent idles/errors in a row."
                    + " The multiplier is then the number of polls that will be skipped before the next actual attempt is happening again."
                    + " When this option is in use then backoffIdleThreshold and/or backoffErrorThreshold must also be configured.")
    private int backoffMultiplier;
    @UriParam(label = "consumer,scheduler",
            description = "The number of subsequent idle polls that should happen before the backoffMultipler should kick-in.")
    private int backoffIdleThreshold;
    @UriParam(label = "consumer,scheduler",
            description = "The number of subsequent error polls (failed due some error) that should happen before the backoffMultipler should kick-in.")
    private int backoffErrorThreshold;
    @UriParam(label = "consumer,scheduler", defaultValue = "0",
            description = "Specifies a maximum limit of number of fires. So if you set it to 1, the scheduler will only fire once."
                    + " If you set it to 5, it will only fire five times. A value of zero or negative means fire forever.")
    private long repeatCount;

    protected ScheduledPollEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    protected ScheduledPollEndpoint() {
    }

    @Override
    protected void configureConsumer(Consumer consumer) throws Exception {
        super.configureConsumer(consumer);
        doConfigureConsumer(consumer);
    }

    @Override
    public void configureProperties(Map<String, Object> options) {
        configureScheduledPollConsumerProperties(options);
        super.configureProperties(options);
    }

    protected void configureScheduledPollConsumerProperties(Map<String, Object> options) {
        // special for scheduled poll consumers as we want to allow end users to configure its options
        // from the URI parameters without the consumer. prefix
        if (!options.isEmpty()) {
            Map<String, Object> schedulerProperties = PropertiesHelper.extractProperties(options, "scheduler.");
            if (!schedulerProperties.isEmpty()) {
                setSchedulerProperties(schedulerProperties);
            }
        }

        // options take precedence
        String schedulerName = (String) options.getOrDefault("scheduler", scheduler);
        if (schedulerName != null) {
            if ("spring".equals(schedulerName)) {
                // special for scheduler if its "spring" or "quartz"
                try {
                    Class<? extends ScheduledPollConsumerScheduler> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(SPRING_SCHEDULER, ScheduledPollConsumerScheduler.class);
                    consumerScheduler = getCamelContext().getInjector().newInstance(clazz);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Cannot load " + SPRING_SCHEDULER + " from classpath. Make sure camel-spring.jar is on the classpath.", e);
                }
            } else if ("quartz".equals(schedulerName)) {
                // special for scheduler if its "spring" or "quartz"
                try {
                    Class<? extends ScheduledPollConsumerScheduler> clazz = getCamelContext().getClassResolver().resolveMandatoryClass(QUARTZ_SCHEDULER, ScheduledPollConsumerScheduler.class);
                    consumerScheduler = getCamelContext().getInjector().newInstance(clazz);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Cannot load " + QUARTZ_SCHEDULER + " from classpath. Make sure camel-quartz.jar is on the classpath.", e);
                }
            } else if (!"none".equals(schedulerName)) {
                // must refer to a custom scheduler by the given name
                if (EndpointHelper.isReferenceParameter(schedulerName)) {
                    schedulerName = schedulerName.substring(1);
                }
                consumerScheduler = CamelContextHelper.mandatoryLookup(getCamelContext(), schedulerName, ScheduledPollConsumerScheduler.class);
            }
        }
    }

    protected void doConfigureConsumer(Consumer consumer) {
        if (consumer instanceof ScheduledPollConsumer) {
            ScheduledPollConsumer spc = (ScheduledPollConsumer) consumer;
            spc.setBackoffErrorThreshold(backoffErrorThreshold);
            spc.setBackoffIdleThreshold(backoffIdleThreshold);
            spc.setBackoffMultiplier(backoffMultiplier);
            spc.setRepeatCount(repeatCount);
            spc.setDelay(delay);
            spc.setGreedy(greedy);
            spc.setInitialDelay(initialDelay);
            spc.setPollStrategy(pollStrategy);
            spc.setRunLoggingLevel(runLoggingLevel);
            spc.setScheduledExecutorService(scheduledExecutorService);
            spc.setSendEmptyMessageWhenIdle(sendEmptyMessageWhenIdle);
            spc.setTimeUnit(timeUnit);
            spc.setUseFixedDelay(useFixedDelay);
            spc.setStartScheduler(startScheduler);
            spc.setScheduler(consumerScheduler);
            spc.setSchedulerProperties(schedulerProperties);
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // noop
    }

    public boolean isStartScheduler() {
        return startScheduler;
    }

    /**
     * Whether the scheduler should be auto started.
     */
    public void setStartScheduler(boolean startScheduler) {
        this.startScheduler = startScheduler;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    /**
     * Milliseconds before the first poll starts.
     * <p/>
     * The default value is 1000.
     */
    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getDelay() {
        return delay;
    }

    /**
     * Milliseconds before the next poll.
     * <p/>
     * The default value is 500.
     */
    public void setDelay(long delay) {
        this.delay = delay;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Time unit for initialDelay and delay options.
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public boolean isUseFixedDelay() {
        return useFixedDelay;
    }

    /**
     * Controls if fixed delay or fixed rate is used. See ScheduledExecutorService in JDK for details.
     */
    public void setUseFixedDelay(boolean useFixedDelay) {
        this.useFixedDelay = useFixedDelay;
    }

    public PollingConsumerPollStrategy getPollStrategy() {
        return pollStrategy;
    }

    /**
     * A pluggable org.apache.camel.PollingConsumerPollingStrategy allowing you to provide your custom implementation
     * to control error handling usually occurred during the poll operation before an Exchange have been created
     * and being routed in Camel. In other words the error occurred while the polling was gathering information,
     * for instance access to a file network failed so Camel cannot access it to scan for files.
     * The default implementation will log the caused exception at WARN level and ignore it.
     */
    public void setPollStrategy(PollingConsumerPollStrategy pollStrategy) {
        this.pollStrategy = pollStrategy;
        // we are allowed to change poll strategy
    }

    public LoggingLevel getRunLoggingLevel() {
        return runLoggingLevel;
    }

    /**
     * The consumer logs a start/complete log line when it polls. This option allows you to configure the logging level for that.
     */
    public void setRunLoggingLevel(LoggingLevel runLoggingLevel) {
        this.runLoggingLevel = runLoggingLevel;
    }

    public boolean isSendEmptyMessageWhenIdle() {
        return sendEmptyMessageWhenIdle;
    }

    /**
     * If the polling consumer did not poll any files, you can enable this option to send an empty message (no body) instead.
     */
    public void setSendEmptyMessageWhenIdle(boolean sendEmptyMessageWhenIdle) {
        this.sendEmptyMessageWhenIdle = sendEmptyMessageWhenIdle;
    }

    public boolean isGreedy() {
        return greedy;
    }

    /**
     * If greedy is enabled, then the ScheduledPollConsumer will run immediately again, if the previous run polled 1 or more messages.
     */
    public void setGreedy(boolean greedy) {
        this.greedy = greedy;
    }

    /**
     * Allow to plugin a custom org.apache.camel.spi.ScheduledPollConsumerScheduler to use as the scheduler for
     * firing when the polling consumer runs. This option is used for referring to one of the built-in schedulers
     * either <tt>spring</tt>, or <tt>quartz</tt>. Using <tt>none</tt> refers to no scheduler to be used.
     *
     * Notice: If using a custom scheduler then the options for initialDelay, useFixedDelay, timeUnit,
     * and scheduledExecutorService may not be in use. Use the text quartz to refer to use the Quartz scheduler;
     * and use the text spring to use the Spring based; and use the text #myScheduler to refer to a custom scheduler
     * by its id in the Registry. See Quartz page for an example.
     */
    public void setScheduler(String schedulerName) {
        this.scheduler = schedulerName;
    }

    public String getScheduler() {
        return scheduler;
    }

    public Map<String, Object> getSchedulerProperties() {
        return schedulerProperties;
    }

    /**
     * To configure additional properties when using a custom scheduler or any of the Quartz, Spring based scheduler.
     */
    public void setSchedulerProperties(Map<String, Object> schedulerProperties) {
        this.schedulerProperties = schedulerProperties;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    /**
     * Allows for configuring a custom/shared thread pool to use for the consumer.
     * By default each consumer has its own single threaded thread pool.
     * This option allows you to share a thread pool among multiple consumers.
     */
    public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public int getBackoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * To let the scheduled polling consumer backoff if there has been a number of subsequent idles/errors in a row.
     * The multiplier is then the number of polls that will be skipped before the next actual attempt is happening again.
     * When this option is in use then backoffIdleThreshold and/or backoffErrorThreshold must also be configured.
     */
    public void setBackoffMultiplier(int backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
    }

    public int getBackoffIdleThreshold() {
        return backoffIdleThreshold;
    }

    /**
     * The number of subsequent idle polls that should happen before the backoffMultipler should kick-in.
     */
    public void setBackoffIdleThreshold(int backoffIdleThreshold) {
        this.backoffIdleThreshold = backoffIdleThreshold;
    }

    public int getBackoffErrorThreshold() {
        return backoffErrorThreshold;
    }

    /**
     * The number of subsequent error polls (failed due some error) that should happen before the backoffMultipler should kick-in.
     */
    public void setBackoffErrorThreshold(int backoffErrorThreshold) {
        this.backoffErrorThreshold = backoffErrorThreshold;
    }

    public long getRepeatCount() {
        return repeatCount;
    }

    /**
     * Specifies a maximum limit of number of fires.
     * So if you set it to 1, the scheduler will only fire once.
     * If you set it to 5, it will only fire five times.
     * A value of zero or negative means fire forever.
     */
    public void setRepeatCount(long repeatCount) {
        this.repeatCount = repeatCount;
    }

}
