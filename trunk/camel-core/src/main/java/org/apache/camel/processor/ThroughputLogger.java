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
package org.apache.camel.processor;

import java.text.NumberFormat;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A logger for logging message throughput.
 *
 * @version 
 */
public class ThroughputLogger extends CamelLogger {
    private static final Logger LOG = LoggerFactory.getLogger(ThroughputLogger.class);

    private final AtomicInteger receivedCounter = new AtomicInteger();
    private NumberFormat numberFormat = NumberFormat.getNumberInstance();
    private long groupReceivedCount;
    private boolean groupActiveOnly;
    private Integer groupSize;
    private long groupDelay = 1000;
    private Long groupInterval;
    private long startTime;
    private long groupStartTime;
    private String action = "Received";
    private String logMessage;
    private CamelContext camelContext;
    private ScheduledExecutorService logSchedulerService;

    public ThroughputLogger() {
    }

    public ThroughputLogger(Logger log) {
        super(log);
    }

    public ThroughputLogger(Logger log, LoggingLevel level) {
        super(log, level);
    }

    public ThroughputLogger(String logName) {
        super(logName);
    }

    public ThroughputLogger(String logName, LoggingLevel level) {
        super(logName, level);
    }

    public ThroughputLogger(String logName, LoggingLevel level, Integer groupSize) {
        super(logName, level);
        setGroupSize(groupSize);
    }

    public ThroughputLogger(CamelContext camelContext, String logName, LoggingLevel level,
                            Long groupInterval, Long groupDelay, Boolean groupActiveOnly) {
        super(logName, level);

        this.camelContext = camelContext;
        setGroupInterval(groupInterval);
        setGroupActiveOnly(groupActiveOnly);
        if (groupDelay != null) {
            setGroupDelay(groupDelay);
        }
    }

    public ThroughputLogger(String logName, int groupSize) {
        super(logName);
        setGroupSize(groupSize);
    }

    public ThroughputLogger(int groupSize) {
        setGroupSize(groupSize);
    }

    @Override
    public void process(Exchange exchange) {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        int receivedCount = receivedCounter.incrementAndGet();

        //only process if groupSize is set...otherwise we're in groupInterval mode
        if (groupSize != null) {
            if (receivedCount % groupSize == 0) {
                logMessage = createLogMessage(exchange, receivedCount);
                super.process(exchange);
            }
        }
    }

    public Integer getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(Integer groupSize) {
        if (groupSize == null || groupSize <= 0) {
            throw new IllegalArgumentException("groupSize must be positive, was: " + groupSize);
        }
        this.groupSize = groupSize;
    }

    public Long getGroupInterval() {
        return groupInterval;
    }

    public void setGroupInterval(Long groupInterval) {
        if (groupInterval == null || groupInterval <= 0) {
            throw new IllegalArgumentException("groupInterval must be positive, was: " + groupInterval);
        }
        this.groupInterval = groupInterval;
    }

    public long getGroupDelay() {
        return groupDelay;
    }

    public void setGroupDelay(long groupDelay) {
        this.groupDelay = groupDelay;
    }

    public boolean getGroupActiveOnly() {
        return groupActiveOnly;
    }

    private void setGroupActiveOnly(boolean groupActiveOnly) {
        this.groupActiveOnly = groupActiveOnly;
    }

    public NumberFormat getNumberFormat() {
        return numberFormat;
    }

    public void setNumberFormat(NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @Override
    protected String logMessage(Exchange exchange) {
        return logMessage;
    }

    @Override
    public void start() throws Exception {
        // if an interval was specified, create a background thread
        if (groupInterval != null) {
            ObjectHelper.notNull(camelContext, "CamelContext", this);

            logSchedulerService = camelContext.getExecutorServiceStrategy().newScheduledThreadPool(this, "ThroughputLogger", 1);
            Runnable scheduledLogTask = new ScheduledLogTask();
            LOG.info("Scheduling throughput log to run every " + groupInterval + " millis.");
            // must use fixed rate to have it trigger at every X interval
            logSchedulerService.scheduleAtFixedRate(scheduledLogTask, groupDelay, groupInterval, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() throws Exception {
        if (logSchedulerService != null) {
            camelContext.getExecutorServiceStrategy().shutdownNow(logSchedulerService);
            logSchedulerService = null;
        }
    }

    protected String createLogMessage(Exchange exchange, int receivedCount) {
        long time = System.currentTimeMillis();
        if (groupStartTime == 0) {
            groupStartTime = startTime;
        }

        double rate = messagesPerSecond(groupSize, groupStartTime, time);
        double average = messagesPerSecond(receivedCount, startTime, time);

        long duration = time - groupStartTime;
        groupStartTime = time;

        return getAction() + ": " + receivedCount + " messages so far. Last group took: " + duration
                + " millis which is: " + numberFormat.format(rate)
                + " messages per second. average: " + numberFormat.format(average);
    }

    /**
     * Background task that logs throughput stats.
     */
    private final class ScheduledLogTask implements Runnable {

        public void run() {
            // only run if CamelContext has been fully started
            if (!camelContext.getStatus().isStarted()) {
                LOG.trace("ThroughputLogger cannot start because CamelContext({}) has not been started yet", camelContext.getName());
                return;
            }

            createGroupIntervalLogMessage();
        }
    }

    protected void createGroupIntervalLogMessage() {
        
        // this indicates that no messages have been received yet...don't log yet
        if (startTime == 0) {
            return;
        }
        
        int receivedCount = receivedCounter.get();

        // if configured, hide log messages when no new messages have been received
        if (groupActiveOnly && receivedCount == groupReceivedCount) {
            return;
        }

        long time = System.currentTimeMillis();
        if (groupStartTime == 0) {
            groupStartTime = startTime;
        }

        long duration = time - groupStartTime;
        long currentCount = receivedCount - groupReceivedCount;
        double rate = messagesPerSecond(currentCount, groupStartTime, time);
        double average = messagesPerSecond(receivedCount, startTime, time);

        groupStartTime = time;
        groupReceivedCount = receivedCount;

        String message = getAction() + ": " + currentCount + " new messages, with total " + receivedCount + " so far. Last group took: " + duration
                + " millis which is: " + numberFormat.format(rate)
                + " messages per second. average: " + numberFormat.format(average);
        log(message);
    }

    protected double messagesPerSecond(long messageCount, long startTime, long endTime) {
        // timeOneMessage = elapsed / messageCount
        // messagePerSend = 1000 / timeOneMessage
        double rate = messageCount * 1000.0;
        rate /= endTime - startTime;
        return rate;
    }

}
