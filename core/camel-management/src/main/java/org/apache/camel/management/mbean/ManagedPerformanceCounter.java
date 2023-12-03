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
package org.apache.camel.management.mbean;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedPerformanceCounterMBean;
import org.apache.camel.management.PerformanceCounter;
import org.apache.camel.spi.ManagementStrategy;
import org.apache.camel.support.ExchangeHelper;

@ManagedResource(description = "Managed PerformanceCounter")
public abstract class ManagedPerformanceCounter extends ManagedCounter
        implements PerformanceCounter, ManagedPerformanceCounterMBean {

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
    private Statistic lastExchangeCreatedTimestamp;
    private Statistic lastExchangeCompletedTimestamp;
    private String lastExchangeCompletedExchangeId;
    private Statistic lastExchangeFailureTimestamp;
    private String lastExchangeFailureExchangeId;
    private boolean statisticsEnabled = true;

    @Override
    public void init(ManagementStrategy strategy) {
        super.init(strategy);
        this.exchangesCompleted = new StatisticCounter();
        this.exchangesFailed = new StatisticCounter();
        this.exchangesInflight = new StatisticCounter();

        this.failuresHandled = new StatisticCounter();
        this.redeliveries = new StatisticCounter();
        this.externalRedeliveries = new StatisticCounter();

        this.minProcessingTime = new StatisticMinimum();
        this.maxProcessingTime = new StatisticMaximum();
        this.totalProcessingTime = new StatisticCounter();
        this.lastProcessingTime = new StatisticValue();
        this.deltaProcessingTime = new StatisticDelta();
        this.meanProcessingTime = new StatisticValue();

        this.firstExchangeCompletedTimestamp = new StatisticValue();
        this.firstExchangeFailureTimestamp = new StatisticValue();
        this.lastExchangeCreatedTimestamp = new StatisticValue();
        this.lastExchangeCompletedTimestamp = new StatisticValue();
        this.lastExchangeFailureTimestamp = new StatisticValue();
    }

    @Override
    public void reset() {
        super.reset();
        exchangesCompleted.reset();
        exchangesFailed.reset();
        // do not reset exchangesInflight
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
        lastExchangeCreatedTimestamp.reset();
        lastExchangeCompletedTimestamp.reset();
        lastExchangeCompletedExchangeId = null;
        lastExchangeFailureTimestamp.reset();
        lastExchangeFailureExchangeId = null;
    }

    @Override
    public long getExchangesCompleted() {
        return exchangesCompleted.getValue();
    }

    @Override
    public long getExchangesFailed() {
        return exchangesFailed.getValue();
    }

    @Override
    public long getExchangesInflight() {
        return exchangesInflight.getValue();
    }

    @Override
    public long getFailuresHandled() {
        return failuresHandled.getValue();
    }

    @Override
    public long getRedeliveries() {
        return redeliveries.getValue();
    }

    @Override
    public long getExternalRedeliveries() {
        return externalRedeliveries.getValue();
    }

    @Override
    public long getMinProcessingTime() {
        return minProcessingTime.getValue();
    }

    @Override
    public long getMeanProcessingTime() {
        return meanProcessingTime.getValue();
    }

    @Override
    public long getMaxProcessingTime() {
        return maxProcessingTime.getValue();
    }

    @Override
    public long getTotalProcessingTime() {
        return totalProcessingTime.getValue();
    }

    @Override
    public long getLastProcessingTime() {
        return lastProcessingTime.getValue();
    }

    @Override
    public long getDeltaProcessingTime() {
        return deltaProcessingTime.getValue();
    }

    @Override
    public long getIdleSince() {
        // must not have any inflight
        if (getExchangesInflight() <= 0) {
            // what is the last time since completed/failed
            long max = Math.max(lastExchangeCompletedTimestamp.getValue(), lastExchangeFailureTimestamp.getValue());
            if (max > 0) {
                long delta = System.currentTimeMillis() - max;
                if (delta > 0) {
                    return delta;
                }
            }
        }
        return -1;
    }

    @Override
    public Date getLastExchangeCreatedTimestamp() {
        long value = lastExchangeCreatedTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @Override
    public Date getLastExchangeCompletedTimestamp() {
        long value = lastExchangeCompletedTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @Override
    public String getLastExchangeCompletedExchangeId() {
        return lastExchangeCompletedExchangeId;
    }

    @Override
    public Date getFirstExchangeCompletedTimestamp() {
        long value = firstExchangeCompletedTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @Override
    public String getFirstExchangeCompletedExchangeId() {
        return firstExchangeCompletedExchangeId;
    }

    @Override
    public Date getLastExchangeFailureTimestamp() {
        long value = lastExchangeFailureTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @Override
    public String getLastExchangeFailureExchangeId() {
        return lastExchangeFailureExchangeId;
    }

    @Override
    public Date getFirstExchangeFailureTimestamp() {
        long value = firstExchangeFailureTimestamp.getValue();
        return value > 0 ? new Date(value) : null;
    }

    @Override
    public String getFirstExchangeFailureExchangeId() {
        return firstExchangeFailureExchangeId;
    }

    @Override
    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    @Override
    public void processExchange(Exchange exchange, String type) {
        exchangesInflight.increment();
        if ("route".equals(type)) {
            long now = System.currentTimeMillis();
            lastExchangeCreatedTimestamp.updateValue(now);
        }
    }

    @Override
    public void completedExchange(Exchange exchange, long time) {
        increment();
        exchangesCompleted.increment();
        exchangesInflight.decrement();

        if (ExchangeHelper.isFailureHandled(exchange)) {
            failuresHandled.increment();
        }
        if (exchange.isExternalRedelivered()) {
            externalRedeliveries.increment();
        }

        minProcessingTime.updateValue(time);
        maxProcessingTime.updateValue(time);
        totalProcessingTime.updateValue(time);
        lastProcessingTime.updateValue(time);
        deltaProcessingTime.updateValue(time);

        long now = System.currentTimeMillis();
        if (!firstExchangeCompletedTimestamp.isUpdated()) {
            firstExchangeCompletedTimestamp.updateValue(now);
        }

        lastExchangeCompletedTimestamp.updateValue(now);
        if (firstExchangeCompletedExchangeId == null) {
            firstExchangeCompletedExchangeId = exchange.getExchangeId();
        }
        lastExchangeCompletedExchangeId = exchange.getExchangeId();

        // update mean
        long mean = 0;
        long completed = exchangesCompleted.getValue();
        if (completed > 0) {
            mean = totalProcessingTime.getValue() / completed;
        }
        meanProcessingTime.updateValue(mean);
    }

    @Override
    public void failedExchange(Exchange exchange) {
        increment();
        exchangesFailed.increment();
        exchangesInflight.decrement();

        if (ExchangeHelper.isRedelivered(exchange)) {
            redeliveries.increment();
        }
        if (exchange.isExternalRedelivered()) {
            externalRedeliveries.increment();
        }

        long now = System.currentTimeMillis();
        if (!firstExchangeFailureTimestamp.isUpdated()) {
            firstExchangeFailureTimestamp.updateValue(now);
        }

        lastExchangeFailureTimestamp.updateValue(now);
        if (firstExchangeFailureExchangeId == null) {
            firstExchangeFailureExchangeId = exchange.getExchangeId();
        }
        lastExchangeFailureExchangeId = exchange.getExchangeId();
    }

    @Override
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
        sb.append(String.format(" idleSince=\"%s\"", getIdleSince()));

        if (fullStats) {
            sb.append(String.format(" startTimestamp=\"%s\"", dateAsString(startTimestamp.getTime())));
            sb.append(String.format(" resetTimestamp=\"%s\"", dateAsString(resetTimestamp.getTime())));
            sb.append(String.format(" firstExchangeCompletedTimestamp=\"%s\"",
                    dateAsString(firstExchangeCompletedTimestamp.getValue())));
            sb.append(String.format(" firstExchangeCompletedExchangeId=\"%s\"", nullSafe(firstExchangeCompletedExchangeId)));
            sb.append(String.format(" firstExchangeFailureTimestamp=\"%s\"",
                    dateAsString(firstExchangeFailureTimestamp.getValue())));
            sb.append(String.format(" firstExchangeFailureExchangeId=\"%s\"", nullSafe(firstExchangeFailureExchangeId)));
            sb.append(String.format(" lastExchangeCreatedTimestamp=\"%s\"",
                    dateAsString(lastExchangeCreatedTimestamp.getValue())));
            sb.append(String.format(" lastExchangeCompletedTimestamp=\"%s\"",
                    dateAsString(lastExchangeCompletedTimestamp.getValue())));
            sb.append(String.format(" lastExchangeCompletedExchangeId=\"%s\"", nullSafe(lastExchangeCompletedExchangeId)));
            sb.append(String.format(" lastExchangeFailureTimestamp=\"%s\"",
                    dateAsString(lastExchangeFailureTimestamp.getValue())));
            sb.append(String.format(" lastExchangeFailureExchangeId=\"%s\"", nullSafe(lastExchangeFailureExchangeId)));
        }
        sb.append("/>");
        return sb.toString();
    }

    private static String dateAsString(long value) {
        if (value <= 0) {
            return "";
        }
        return new SimpleDateFormat(TIMESTAMP_FORMAT).format(value);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

}
