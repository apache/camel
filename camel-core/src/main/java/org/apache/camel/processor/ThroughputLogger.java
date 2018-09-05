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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.CamelLogger;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A logger for logging message throughput.
 *
 * @version 
 */
public class ThroughputLogger extends ServiceSupport implements AsyncProcessor, IdAware {
    private static final Logger LOG = LoggerFactory.getLogger(ThroughputLogger.class);

    private String id;
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
    private CamelContext camelContext;
    private ScheduledExecutorService logSchedulerService;
    private CamelLogger log;
    private String lastLogMessage;
    private double rate;
    private double average;

    public ThroughputLogger(CamelLogger log) {
        this.log = log;
    }

    public ThroughputLogger(CamelLogger log, Integer groupSize) {
        this(log);
        setGroupSize(groupSize);
    }

    public ThroughputLogger(CamelLogger log, CamelContext camelContext, Long groupInterval, Long groupDelay, Boolean groupActiveOnly) {
        this(log);
        this.camelContext = camelContext;
        setGroupInterval(groupInterval);
        setGroupActiveOnly(groupActiveOnly);
        if (groupDelay != null) {
            setGroupDelay(groupDelay);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }
        int receivedCount = receivedCounter.incrementAndGet();

        //only process if groupSize is set...otherwise we're in groupInterval mode
        if (groupSize != null) {
            if (receivedCount % groupSize == 0) {
                lastLogMessage = createLogMessage(exchange, receivedCount);
                log.log(lastLogMessage);
            }
        }

        callback.done(true);
        return true;
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

    public void reset() {
        startTime = 0;
        receivedCounter.set(0);
        groupStartTime = 0;
        groupReceivedCount = 0;
        average = 0.0d;
        rate = 0.0d;
        lastLogMessage = null;
    }

    public double getRate() {
        return rate;
    }

    public double getAverage() {
        return average;
    }

    public int getReceivedCounter() {
        return receivedCounter.get();
    }

    public String getLastLogMessage() {
        return lastLogMessage;
    }

    @Override
    public void doStart() throws Exception {
        // if an interval was specified, create a background thread
        if (groupInterval != null) {
            ObjectHelper.notNull(camelContext, "CamelContext", this);

            logSchedulerService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "ThroughputLogger");
            Runnable scheduledLogTask = new ScheduledLogTask();
            LOG.info("Scheduling throughput log to run every {} millis.", groupInterval);
            // must use fixed rate to have it trigger at every X interval
            logSchedulerService.scheduleAtFixedRate(scheduledLogTask, groupDelay, groupInterval, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void doStop() throws Exception {
        if (logSchedulerService != null) {
            camelContext.getExecutorServiceManager().shutdown(logSchedulerService);
            logSchedulerService = null;
        }
    }

    protected String createLogMessage(Exchange exchange, int receivedCount) {
        long time = System.currentTimeMillis();
        if (groupStartTime == 0) {
            groupStartTime = startTime;
        }

        rate = messagesPerSecond(groupSize, groupStartTime, time);
        average = messagesPerSecond(receivedCount, startTime, time);

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
        rate = messagesPerSecond(currentCount, groupStartTime, time);
        average = messagesPerSecond(receivedCount, startTime, time);

        groupStartTime = time;
        groupReceivedCount = receivedCount;

        lastLogMessage = getAction() + ": " + currentCount + " new messages, with total " + receivedCount + " so far. Last group took: " + duration
                + " millis which is: " + numberFormat.format(rate)
                + " messages per second. average: " + numberFormat.format(average);
        log.log(lastLogMessage);
    }

    protected double messagesPerSecond(long messageCount, long startTime, long endTime) {
        // timeOneMessage = elapsed / messageCount
        // messagePerSend = 1000 / timeOneMessage
        double rate = messageCount * 1000.0;
        rate /= endTime - startTime;
        return rate;
    }

}
