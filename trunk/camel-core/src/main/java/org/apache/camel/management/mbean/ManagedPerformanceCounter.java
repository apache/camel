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
package org.apache.camel.management.mbean;

import java.util.Date;

import org.apache.camel.management.PerformanceCounter;
import org.apache.camel.spi.ManagementStrategy;
import org.fusesource.commons.management.Statistic;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(description = "PerformanceCounter")
public abstract class ManagedPerformanceCounter extends ManagedCounter implements PerformanceCounter {
    private Statistic exchangesCompleted;
    private Statistic exchangesFailed;
    private Statistic minProcessingTime;
    private Statistic maxProcessingTime;
    private Statistic totalProcessingTime;
    private Statistic lastProcessingTime;
    private Statistic meanProcessingTime;
    private Statistic firstExchangeCompletedTimestamp;
    private Statistic firstExchangeFailureTimestamp;
    private Statistic lastExchangeCompletedTimestamp;
    private Statistic lastExchangeFailureTimestamp;
    private boolean statisticsEnabled = true;

    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        this.exchangesCompleted = strategy.createStatistic("org.apache.camel.exchangesCompleted", this, Statistic.UpdateMode.COUNTER);
        this.exchangesFailed = strategy.createStatistic("org.apache.camel.exchangesFailed", this, Statistic.UpdateMode.COUNTER);
        this.minProcessingTime = strategy.createStatistic("org.apache.camel.minimumProcessingTime", this, Statistic.UpdateMode.MINIMUM);
        this.maxProcessingTime = strategy.createStatistic("org.apache.camel.maximumProcessingTime", this, Statistic.UpdateMode.MAXIMUM);
        this.totalProcessingTime = strategy.createStatistic("org.apache.camel.totalProcessingTime", this, Statistic.UpdateMode.COUNTER);
        this.lastProcessingTime = strategy.createStatistic("org.apache.camel.lastProcessingTime", this, Statistic.UpdateMode.VALUE);
        this.meanProcessingTime = strategy.createStatistic("org.apache.camel.meanProcessingTime", this, Statistic.UpdateMode.VALUE);

        this.firstExchangeCompletedTimestamp = strategy.createStatistic("org.apache.camel.firstExchangeCompletedTimestamp", this, Statistic.UpdateMode.VALUE);
        this.firstExchangeFailureTimestamp = strategy.createStatistic("org.apache.camel.firstExchangeFailureTimestamp", this, Statistic.UpdateMode.VALUE);
        this.lastExchangeCompletedTimestamp = strategy.createStatistic("org.apache.camel.lastExchangeCompletedTimestamp", this, Statistic.UpdateMode.VALUE);
        this.lastExchangeFailureTimestamp = strategy.createStatistic("org.apache.camel.lastExchangeFailureTimestamp", this, Statistic.UpdateMode.VALUE);
    }

    @Override
    @ManagedOperation(description = "Reset counters")
    public synchronized void reset() {
        super.reset();
        exchangesCompleted.reset();
        exchangesFailed.reset();
        minProcessingTime.reset();
        maxProcessingTime.reset();
        totalProcessingTime.reset();
        lastProcessingTime.reset();
        meanProcessingTime.reset();
        firstExchangeCompletedTimestamp.reset();
        firstExchangeFailureTimestamp.reset();
        lastExchangeCompletedTimestamp.reset();
        lastExchangeFailureTimestamp.reset();
    }

    @ManagedAttribute(description = "Number of completed exchanges")
    public long getExchangesCompleted() throws Exception {
        return exchangesCompleted.getValue();
    }

    @ManagedAttribute(description = "Number of failed exchanges")
    public long getExchangesFailed() throws Exception {
        return exchangesFailed.getValue();
    }

    @ManagedAttribute(description = "Min Processing Time [milliseconds]")
    public long getMinProcessingTime() throws Exception {
        return minProcessingTime.getValue();
    }

    @ManagedAttribute(description = "Mean Processing Time [milliseconds]")
    public long getMeanProcessingTime() throws Exception {
        return meanProcessingTime.getValue();
    }

    @ManagedAttribute(description = "Max Processing Time [milliseconds]")
    public long getMaxProcessingTime() throws Exception {
        return maxProcessingTime.getValue();
    }

    @ManagedAttribute(description = "Total Processing Time [milliseconds]")
    public long getTotalProcessingTime() throws Exception {
        return totalProcessingTime.getValue();
    }

    @ManagedAttribute(description = "Last Processing Time [milliseconds]")
    public long getLastProcessingTime() throws Exception {
        return lastProcessingTime.getValue();
    }

    @ManagedAttribute(description = "Last Exchange Completed Timestamp")
    public Date getLastExchangeCompletedTimestamp() {
        long value = lastExchangeCompletedTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @ManagedAttribute(description = "First Exchange Completed Timestamp")
    public Date getFirstExchangeCompletedTimestamp() {
        long value = firstExchangeCompletedTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @ManagedAttribute(description = "Last Exchange Failed Timestamp")
    public Date getLastExchangeFailureTimestamp() {
        long value = lastExchangeFailureTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @ManagedAttribute(description = "First Exchange Failed Timestamp")
    public Date getFirstExchangeFailureTimestamp() {
        long value = firstExchangeFailureTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @ManagedAttribute(description = "Statistics enabled")
    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    @ManagedAttribute(description = "Statistics enabled")
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    /**
     * This method is called when an exchange has been processed successfully.
     * 
     * @param time in milliseconds it spent on processing the exchange
     */
    public synchronized void completedExchange(long time) {
        increment();
        exchangesCompleted.increment();

        minProcessingTime.updateValue(time);
        maxProcessingTime.updateValue(time);
        totalProcessingTime.updateValue(time);
        lastProcessingTime.updateValue(time);

        long now = new Date().getTime();
        if (firstExchangeCompletedTimestamp.getUpdateCount() == 0) {
            firstExchangeCompletedTimestamp.updateValue(now);
        }

        lastExchangeCompletedTimestamp.updateValue(now);

        // update mean
        long count = exchangesCompleted.getValue();
        long mean = count > 0 ? totalProcessingTime.getValue() / count : 0;
        meanProcessingTime.updateValue(mean);
    }

    /**
     * This method is called when an exchange has been processed and failed.
     */
    public synchronized void failedExchange() {
        increment();
        exchangesFailed.increment();

        long now = new Date().getTime();
        if (firstExchangeFailureTimestamp.getUpdateCount() == 0) {
            firstExchangeFailureTimestamp.updateValue(now);
        }

        lastExchangeFailureTimestamp.updateValue(now);
    }

}
