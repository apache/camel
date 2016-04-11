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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.PerformanceCounter;
import org.apache.camel.api.management.mbean.ManagedPerformanceCounterMBean;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.util.ExchangeHelper;

@ManagedResource(description = "Managed PerformanceCounter")
public abstract class ManagedPerformanceCounter extends ManagedCounter implements PerformanceCounter, ManagedPerformanceCounterMBean {

    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private Statistic exchangesCompleted;
    private Statistic exchangesFailed;
    private Statistic exchangesInflight;
    private Statistic failuresHandled;
    private Statistic redeliveries;
    private Statistic externalRedeliveries;
    private Statistic minProcessingTime;
    private Statistic maxProcessingTime;
    private Statistic totalProcessingTime;
    private Statistic lastProcessingTime;
    private Statistic deltaProcessingTime;
    private Statistic meanProcessingTime;
    private Statistic firstExchangeCompletedTimestamp;
    private String firstExchangeCompletedExchangeId;
    private Statistic firstExchangeFailureTimestamp;
    private String firstExchangeFailureExchangeId;
    private Statistic lastExchangeCompletedTimestamp;
    private String lastExchangeCompletedExchangeId;
    private Statistic lastExchangeFailureTimestamp;
    private String lastExchangeFailureExchangeId;
    private boolean statisticsEnabled = true;

    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        this.exchangesCompleted = new Statistic("org.apache.camel.exchangesCompleted", this, Statistic.UpdateMode.COUNTER);
        this.exchangesFailed = new Statistic("org.apache.camel.exchangesFailed", this, Statistic.UpdateMode.COUNTER);
        this.exchangesInflight = new Statistic("org.apache.camel.exchangesInflight", this, Statistic.UpdateMode.COUNTER);

        this.failuresHandled = new Statistic("org.apache.camel.failuresHandled", this, Statistic.UpdateMode.COUNTER);
        this.redeliveries = new Statistic("org.apache.camel.redeliveries", this, Statistic.UpdateMode.COUNTER);
        this.externalRedeliveries = new Statistic("org.apache.camel.externalRedeliveries", this, Statistic.UpdateMode.COUNTER);

        this.minProcessingTime = new Statistic("org.apache.camel.minimumProcessingTime", this, Statistic.UpdateMode.MINIMUM);
        this.maxProcessingTime = new Statistic("org.apache.camel.maximumProcessingTime", this, Statistic.UpdateMode.MAXIMUM);
        this.totalProcessingTime = new Statistic("org.apache.camel.totalProcessingTime", this, Statistic.UpdateMode.COUNTER);
        this.lastProcessingTime = new Statistic("org.apache.camel.lastProcessingTime", this, Statistic.UpdateMode.VALUE);
        this.deltaProcessingTime = new Statistic("org.apache.camel.deltaProcessingTime", this, Statistic.UpdateMode.DELTA);
        this.meanProcessingTime = new Statistic("org.apache.camel.meanProcessingTime", this, Statistic.UpdateMode.VALUE);

        this.firstExchangeCompletedTimestamp = new Statistic("org.apache.camel.firstExchangeCompletedTimestamp", this, Statistic.UpdateMode.VALUE);
        this.firstExchangeFailureTimestamp = new Statistic("org.apache.camel.firstExchangeFailureTimestamp", this, Statistic.UpdateMode.VALUE);
        this.lastExchangeCompletedTimestamp = new Statistic("org.apache.camel.lastExchangeCompletedTimestamp", this, Statistic.UpdateMode.VALUE);
        this.lastExchangeFailureTimestamp = new Statistic("org.apache.camel.lastExchangeFailureTimestamp", this, Statistic.UpdateMode.VALUE);
    }

    @Override
    public synchronized void reset() {
        super.reset();
        exchangesCompleted.reset();
        exchangesFailed.reset();
        exchangesInflight.reset();
        failuresHandled.reset();
        redeliveries.reset();
        externalRedeliveries.reset();
        minProcessingTime.reset();
        maxProcessingTime.reset();
        totalProcessingTime.reset();
        lastProcessingTime.reset();
        deltaProcessingTime.reset();
        meanProcessingTime.reset();
        firstExchangeCompletedTimestamp.reset();
        firstExchangeCompletedExchangeId = null;
        firstExchangeFailureTimestamp.reset();
        firstExchangeFailureExchangeId = null;
        lastExchangeCompletedTimestamp.reset();
        lastExchangeCompletedExchangeId = null;
        lastExchangeFailureTimestamp.reset();
        lastExchangeFailureExchangeId = null;
    }

    public long getExchangesCompleted() throws Exception {
        return exchangesCompleted.getValue();
    }

    public long getExchangesFailed() throws Exception {
        return exchangesFailed.getValue();
    }

    public long getExchangesInflight() {
        return exchangesInflight.getValue();
    }

    public long getFailuresHandled() throws Exception {
        return failuresHandled.getValue();
    }

    public long getRedeliveries() throws Exception {
        return redeliveries.getValue();
    }

    public long getExternalRedeliveries() throws Exception {
        return externalRedeliveries.getValue();
    }

    public long getMinProcessingTime() throws Exception {
        return minProcessingTime.getValue();
    }

    public long getMeanProcessingTime() throws Exception {
        return meanProcessingTime.getValue();
    }

    public long getMaxProcessingTime() throws Exception {
        return maxProcessingTime.getValue();
    }

    public long getTotalProcessingTime() throws Exception {
        return totalProcessingTime.getValue();
    }

    public long getLastProcessingTime() throws Exception {
        return lastProcessingTime.getValue();
    }

    public long getDeltaProcessingTime() throws Exception {
        return deltaProcessingTime.getValue();
    }

    public Date getLastExchangeCompletedTimestamp() {
        long value = lastExchangeCompletedTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    public String getLastExchangeCompletedExchangeId() {
        return lastExchangeCompletedExchangeId;
    }

    public Date getFirstExchangeCompletedTimestamp() {
        long value = firstExchangeCompletedTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    public String getFirstExchangeCompletedExchangeId() {
        return firstExchangeCompletedExchangeId;
    }

    public Date getLastExchangeFailureTimestamp() {
        long value = lastExchangeFailureTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    public String getLastExchangeFailureExchangeId() {
        return lastExchangeFailureExchangeId;
    }

    public Date getFirstExchangeFailureTimestamp() {
        long value = firstExchangeFailureTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    public String getFirstExchangeFailureExchangeId() {
        return firstExchangeFailureExchangeId;
    }

    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    public void setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    public synchronized void processExchange(Exchange exchange) {
        exchangesInflight.increment();
    }

    public synchronized void completedExchange(Exchange exchange, long time) {
        increment();
        exchangesCompleted.increment();
        exchangesInflight.decrement();

        if (ExchangeHelper.isFailureHandled(exchange)) {
            failuresHandled.increment();
        }
        Boolean externalRedelivered = exchange.isExternalRedelivered();
        if (externalRedelivered != null && externalRedelivered) {
            externalRedeliveries.increment();
        }

        minProcessingTime.updateValue(time);
        maxProcessingTime.updateValue(time);
        totalProcessingTime.updateValue(time);
        lastProcessingTime.updateValue(time);
        deltaProcessingTime.updateValue(time);

        long now = new Date().getTime();
        if (firstExchangeCompletedTimestamp.getUpdateCount() == 0) {
            firstExchangeCompletedTimestamp.updateValue(now);
        }

        lastExchangeCompletedTimestamp.updateValue(now);
        if (firstExchangeCompletedExchangeId == null) {
            firstExchangeCompletedExchangeId = exchange.getExchangeId();
        }
        lastExchangeCompletedExchangeId = exchange.getExchangeId();

        // update mean
        long count = exchangesCompleted.getValue();
        long mean = count > 0 ? totalProcessingTime.getValue() / count : 0;
        meanProcessingTime.updateValue(mean);
    }

    public synchronized void failedExchange(Exchange exchange) {
        increment();
        exchangesFailed.increment();
        exchangesInflight.decrement();

        if (ExchangeHelper.isRedelivered(exchange)) {
            redeliveries.increment();
        }
        Boolean externalRedelivered = exchange.isExternalRedelivered();
        if (externalRedelivered != null && externalRedelivered) {
            externalRedeliveries.increment();
        }

        long now = new Date().getTime();
        if (firstExchangeFailureTimestamp.getUpdateCount() == 0) {
            firstExchangeFailureTimestamp.updateValue(now);
        }

        lastExchangeFailureTimestamp.updateValue(now);
        if (firstExchangeFailureExchangeId == null) {
            firstExchangeFailureExchangeId = exchange.getExchangeId();
        }
        lastExchangeFailureExchangeId = exchange.getExchangeId();
    }

    public String dumpStatsAsXml(boolean fullStats) {
        StringBuilder sb = new StringBuilder();
        sb.append("<stats ");
        sb.append(String.format("exchangesCompleted=\"%s\"", exchangesCompleted.getValue()));
        sb.append(String.format(" exchangesFailed=\"%s\"", exchangesFailed.getValue()));
        sb.append(String.format(" failuresHandled=\"%s\"", failuresHandled.getValue()));
        sb.append(String.format(" redeliveries=\"%s\"", redeliveries.getValue()));
        sb.append(String.format(" externalRedeliveries=\"%s\"", externalRedeliveries.getValue()));
        sb.append(String.format(" minProcessingTime=\"%s\"", minProcessingTime.getValue()));
        sb.append(String.format(" maxProcessingTime=\"%s\"", maxProcessingTime.getValue()));
        sb.append(String.format(" totalProcessingTime=\"%s\"", totalProcessingTime.getValue()));
        sb.append(String.format(" lastProcessingTime=\"%s\"", lastProcessingTime.getValue()));
        sb.append(String.format(" deltaProcessingTime=\"%s\"", deltaProcessingTime.getValue()));
        sb.append(String.format(" meanProcessingTime=\"%s\"", meanProcessingTime.getValue()));

        if (fullStats) {
            sb.append(String.format(" startTimestamp=\"%s\"", dateAsString(startTimestamp.getValue())));
            sb.append(String.format(" resetTimestamp=\"%s\"", dateAsString(resetTimestamp.getValue())));
            sb.append(String.format(" firstExchangeCompletedTimestamp=\"%s\"", dateAsString(firstExchangeCompletedTimestamp.getValue())));
            sb.append(String.format(" firstExchangeCompletedExchangeId=\"%s\"", nullSafe(firstExchangeCompletedExchangeId)));
            sb.append(String.format(" firstExchangeFailureTimestamp=\"%s\"", dateAsString(firstExchangeFailureTimestamp.getValue())));
            sb.append(String.format(" firstExchangeFailureExchangeId=\"%s\"", nullSafe(firstExchangeFailureExchangeId)));
            sb.append(String.format(" lastExchangeCompletedTimestamp=\"%s\"", dateAsString(lastExchangeCompletedTimestamp.getValue())));
            sb.append(String.format(" lastExchangeCompletedExchangeId=\"%s\"", nullSafe(lastExchangeCompletedExchangeId)));
            sb.append(String.format(" lastExchangeFailureTimestamp=\"%s\"", dateAsString(lastExchangeFailureTimestamp.getValue())));
            sb.append(String.format(" lastExchangeFailureExchangeId=\"%s\"", nullSafe(lastExchangeFailureExchangeId)));
        }
        sb.append("/>");
        return sb.toString();
    }

    private static String dateAsString(long value) {
        if (value == 0) {
            return "";
        }
        return new SimpleDateFormat(TIMESTAMP_FORMAT).format(value);
    }
    
    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

}
