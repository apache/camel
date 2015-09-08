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
package org.apache.camel.util;

import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A model of a route stat dump from {@link org.apache.camel.api.management.mbean.ManagedRouteMBean#dumpRouteStatsAsXml(boolean, boolean)}.
 */
@XmlRootElement(name = "routeStat")
@XmlAccessorType(XmlAccessType.FIELD)
public final class RouteStatDump {

    @XmlAttribute
    private String id;

    @XmlAttribute
    private String state;

    @XmlAttribute
    private Long exchangesCompleted;

    @XmlAttribute
    private Long exchangesFailed;

    @XmlAttribute
    private Long failuresHandled;

    @XmlAttribute
    private Long redeliveries;

    @XmlAttribute
    private Long minProcessingTime;

    @XmlAttribute
    private Long maxProcessingTime;

    @XmlAttribute
    private Long totalProcessingTime;

    @XmlAttribute
    private Long lastProcessingTime;

    @XmlAttribute
    private Long deltaProcessingTime;

    @XmlAttribute
    private Long meanProcessingTime;

    @XmlAttribute
    private Long exchangesInflight;

    @XmlAttribute
    private Long selfProcessingTime;

    @XmlAttribute
    private String startTimestamp;

    @XmlAttribute
    private String resetTimestamp;

    @XmlAttribute
    private String firstExchangeCompletedTimestamp;

    @XmlAttribute
    private String firstExchangeCompletedExchangeId;

    @XmlAttribute
    private String firstExchangeFailureTimestamp;

    @XmlAttribute
    private String firstExchangeFailureExchangeId;

    @XmlAttribute
    private String lastExchangeCompletedTimestamp;

    @XmlAttribute
    private String lastExchangeCompletedExchangeId;

    @XmlAttribute
    private String lastExchangeFailureTimestamp;

    @XmlAttribute
    private String lastExchangeFailureExchangeId;

    @XmlElementWrapper(name = "processorStats")
    @XmlElements({
            @XmlElement(type = ProcessorStatDump.class, name = "processorStat")
        })
    private List<ProcessorStatDump> processorStats;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Long getExchangesCompleted() {
        return exchangesCompleted;
    }

    public void setExchangesCompleted(Long exchangesCompleted) {
        this.exchangesCompleted = exchangesCompleted;
    }

    public Long getExchangesFailed() {
        return exchangesFailed;
    }

    public void setExchangesFailed(Long exchangesFailed) {
        this.exchangesFailed = exchangesFailed;
    }

    public Long getFailuresHandled() {
        return failuresHandled;
    }

    public void setFailuresHandled(Long failuresHandled) {
        this.failuresHandled = failuresHandled;
    }

    public Long getRedeliveries() {
        return redeliveries;
    }

    public void setRedeliveries(Long redeliveries) {
        this.redeliveries = redeliveries;
    }

    public Long getMinProcessingTime() {
        return minProcessingTime;
    }

    public void setMinProcessingTime(Long minProcessingTime) {
        this.minProcessingTime = minProcessingTime;
    }

    public Long getMaxProcessingTime() {
        return maxProcessingTime;
    }

    public void setMaxProcessingTime(Long maxProcessingTime) {
        this.maxProcessingTime = maxProcessingTime;
    }

    public Long getTotalProcessingTime() {
        return totalProcessingTime;
    }

    public void setTotalProcessingTime(Long totalProcessingTime) {
        this.totalProcessingTime = totalProcessingTime;
    }

    public Long getLastProcessingTime() {
        return lastProcessingTime;
    }

    public void setLastProcessingTime(Long lastProcessingTime) {
        this.lastProcessingTime = lastProcessingTime;
    }

    public Long getDeltaProcessingTime() {
        return deltaProcessingTime;
    }

    public void setDeltaProcessingTime(Long deltaProcessingTime) {
        this.deltaProcessingTime = deltaProcessingTime;
    }

    public Long getMeanProcessingTime() {
        return meanProcessingTime;
    }

    public void setMeanProcessingTime(Long meanProcessingTime) {
        this.meanProcessingTime = meanProcessingTime;
    }

    public Long getSelfProcessingTime() {
        return selfProcessingTime;
    }

    public void setSelfProcessingTime(Long selfProcessingTime) {
        this.selfProcessingTime = selfProcessingTime;
    }

    public Long getExchangesInflight() {
        return exchangesInflight;
    }

    public void setExchangesInflight(Long exchangesInflight) {
        this.exchangesInflight = exchangesInflight;
    }

    public String getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(String startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public String getResetTimestamp() {
        return resetTimestamp;
    }

    public void setResetTimestamp(String resetTimestamp) {
        this.resetTimestamp = resetTimestamp;
    }

    public String getFirstExchangeCompletedTimestamp() {
        return firstExchangeCompletedTimestamp;
    }

    public void setFirstExchangeCompletedTimestamp(String firstExchangeCompletedTimestamp) {
        this.firstExchangeCompletedTimestamp = firstExchangeCompletedTimestamp;
    }

    public String getFirstExchangeCompletedExchangeId() {
        return firstExchangeCompletedExchangeId;
    }

    public void setFirstExchangeCompletedExchangeId(String firstExchangeCompletedExchangeId) {
        this.firstExchangeCompletedExchangeId = firstExchangeCompletedExchangeId;
    }

    public String getFirstExchangeFailureTimestamp() {
        return firstExchangeFailureTimestamp;
    }

    public void setFirstExchangeFailureTimestamp(String firstExchangeFailureTimestamp) {
        this.firstExchangeFailureTimestamp = firstExchangeFailureTimestamp;
    }

    public String getFirstExchangeFailureExchangeId() {
        return firstExchangeFailureExchangeId;
    }

    public void setFirstExchangeFailureExchangeId(String firstExchangeFailureExchangeId) {
        this.firstExchangeFailureExchangeId = firstExchangeFailureExchangeId;
    }

    public String getLastExchangeCompletedTimestamp() {
        return lastExchangeCompletedTimestamp;
    }

    public void setLastExchangeCompletedTimestamp(String lastExchangeCompletedTimestamp) {
        this.lastExchangeCompletedTimestamp = lastExchangeCompletedTimestamp;
    }

    public String getLastExchangeCompletedExchangeId() {
        return lastExchangeCompletedExchangeId;
    }

    public void setLastExchangeCompletedExchangeId(String lastExchangeCompletedExchangeId) {
        this.lastExchangeCompletedExchangeId = lastExchangeCompletedExchangeId;
    }

    public String getLastExchangeFailureTimestamp() {
        return lastExchangeFailureTimestamp;
    }

    public void setLastExchangeFailureTimestamp(String lastExchangeFailureTimestamp) {
        this.lastExchangeFailureTimestamp = lastExchangeFailureTimestamp;
    }

    public String getLastExchangeFailureExchangeId() {
        return lastExchangeFailureExchangeId;
    }

    public void setLastExchangeFailureExchangeId(String lastExchangeFailureExchangeId) {
        this.lastExchangeFailureExchangeId = lastExchangeFailureExchangeId;
    }

    public List<ProcessorStatDump> getProcessorStats() {
        return processorStats;
    }

    public void setProcessorStats(List<ProcessorStatDump> processorStats) {
        this.processorStats = processorStats;
    }

}
