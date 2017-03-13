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
package org.apache.camel.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

import org.apache.camel.spi.Metadata;

@XmlAccessorType(XmlAccessType.FIELD)
public class HystrixConfigurationCommon extends IdentifiedType {

    @XmlAttribute @Metadata(defaultValue = "CamelHystrix")
    private String groupKey;
    @XmlAttribute @Metadata(defaultValue = "CamelHystrix")
    private String threadPoolKey;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "true")
    private Boolean circuitBreakerEnabled;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "50")
    private Integer circuitBreakerErrorThresholdPercentage;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "true")
    private Boolean circuitBreakerForceClosed;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "false")
    private Boolean circuitBreakerForceOpen;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "20")
    private Integer circuitBreakerRequestVolumeThreshold;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "5000")
    private Integer circuitBreakerSleepWindowInMilliseconds;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "20")
    private Integer executionIsolationSemaphoreMaxConcurrentRequests;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "THREAD", enums = "THREAD,SEMAPHORE")
    private String executionIsolationStrategy;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "true")
    private Boolean executionIsolationThreadInterruptOnTimeout;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "1000")
    private Integer executionTimeoutInMilliseconds;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "true")
    private Boolean executionTimeoutEnabled;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "10")
    private Integer fallbackIsolationSemaphoreMaxConcurrentRequests;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "true")
    private Boolean fallbackEnabled;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "500")
    private Integer metricsHealthSnapshotIntervalInMilliseconds;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "10")
    private Integer metricsRollingPercentileBucketSize;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "true")
    private Boolean metricsRollingPercentileEnabled;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "10000")
    private Integer metricsRollingPercentileWindowInMilliseconds;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "6")
    private Integer metricsRollingPercentileWindowBuckets;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "10000")
    private Integer metricsRollingStatisticalWindowInMilliseconds;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "10")
    private Integer metricsRollingStatisticalWindowBuckets;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "true")
    private Boolean requestLogEnabled;

    // thread-pool

    @XmlAttribute
    @Metadata(label = "threadpool", defaultValue = "10")
    private Integer corePoolSize;
    @XmlAttribute
    @Metadata(label = "threadpool", defaultValue = "10")
    private Integer maximumSize;
    @XmlAttribute
    @Metadata(label = "threadpool", defaultValue = "1")
    private Integer keepAliveTime;
    @XmlAttribute
    @Metadata(label = "threadpool", defaultValue = "-1")
    private Integer maxQueueSize;
    @XmlAttribute
    @Metadata(label = "threadpool", defaultValue = "5")
    private Integer queueSizeRejectionThreshold;
    @XmlAttribute
    @Metadata(label = "threadpool", defaultValue = "10000")
    private Integer threadPoolRollingNumberStatisticalWindowInMilliseconds;
    @XmlAttribute
    @Metadata(label = "threadpool", defaultValue = "10")
    private Integer threadPoolRollingNumberStatisticalWindowBuckets;
    @XmlAttribute
    @Metadata(label = "threadpool", defaultValue = "false")
    private Boolean allowMaximumSizeToDivergeFromCoreSize;


    // Getter/Setter
    // -------------------------------------------------------------------------

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public String getThreadPoolKey() {
        return threadPoolKey;
    }

    public void setThreadPoolKey(String threadPoolKey) {
        this.threadPoolKey = threadPoolKey;
    }

    public Boolean getCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    public void setCircuitBreakerEnabled(Boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public Integer getCircuitBreakerErrorThresholdPercentage() {
        return circuitBreakerErrorThresholdPercentage;
    }

    public void setCircuitBreakerErrorThresholdPercentage(Integer circuitBreakerErrorThresholdPercentage) {
        this.circuitBreakerErrorThresholdPercentage = circuitBreakerErrorThresholdPercentage;
    }

    public Boolean getCircuitBreakerForceClosed() {
        return circuitBreakerForceClosed;
    }

    public void setCircuitBreakerForceClosed(Boolean circuitBreakerForceClosed) {
        this.circuitBreakerForceClosed = circuitBreakerForceClosed;
    }

    public Boolean getCircuitBreakerForceOpen() {
        return circuitBreakerForceOpen;
    }

    public void setCircuitBreakerForceOpen(Boolean circuitBreakerForceOpen) {
        this.circuitBreakerForceOpen = circuitBreakerForceOpen;
    }

    public Integer getCircuitBreakerRequestVolumeThreshold() {
        return circuitBreakerRequestVolumeThreshold;
    }

    public void setCircuitBreakerRequestVolumeThreshold(Integer circuitBreakerRequestVolumeThreshold) {
        this.circuitBreakerRequestVolumeThreshold = circuitBreakerRequestVolumeThreshold;
    }

    public Integer getCircuitBreakerSleepWindowInMilliseconds() {
        return circuitBreakerSleepWindowInMilliseconds;
    }

    public void setCircuitBreakerSleepWindowInMilliseconds(Integer circuitBreakerSleepWindowInMilliseconds) {
        this.circuitBreakerSleepWindowInMilliseconds = circuitBreakerSleepWindowInMilliseconds;
    }

    public Integer getExecutionIsolationSemaphoreMaxConcurrentRequests() {
        return executionIsolationSemaphoreMaxConcurrentRequests;
    }

    public void setExecutionIsolationSemaphoreMaxConcurrentRequests(Integer executionIsolationSemaphoreMaxConcurrentRequests) {
        this.executionIsolationSemaphoreMaxConcurrentRequests = executionIsolationSemaphoreMaxConcurrentRequests;
    }

    public String getExecutionIsolationStrategy() {
        return executionIsolationStrategy;
    }

    public void setExecutionIsolationStrategy(String executionIsolationStrategy) {
        this.executionIsolationStrategy = executionIsolationStrategy;
    }

    public Boolean getExecutionIsolationThreadInterruptOnTimeout() {
        return executionIsolationThreadInterruptOnTimeout;
    }

    public void setExecutionIsolationThreadInterruptOnTimeout(Boolean executionIsolationThreadInterruptOnTimeout) {
        this.executionIsolationThreadInterruptOnTimeout = executionIsolationThreadInterruptOnTimeout;
    }

    public Integer getExecutionTimeoutInMilliseconds() {
        return executionTimeoutInMilliseconds;
    }

    public void setExecutionTimeoutInMilliseconds(Integer executionTimeoutInMilliseconds) {
        this.executionTimeoutInMilliseconds = executionTimeoutInMilliseconds;
    }

    public Boolean getExecutionTimeoutEnabled() {
        return executionTimeoutEnabled;
    }

    public void setExecutionTimeoutEnabled(Boolean executionTimeoutEnabled) {
        this.executionTimeoutEnabled = executionTimeoutEnabled;
    }

    public Integer getFallbackIsolationSemaphoreMaxConcurrentRequests() {
        return fallbackIsolationSemaphoreMaxConcurrentRequests;
    }

    public void setFallbackIsolationSemaphoreMaxConcurrentRequests(Integer fallbackIsolationSemaphoreMaxConcurrentRequests) {
        this.fallbackIsolationSemaphoreMaxConcurrentRequests = fallbackIsolationSemaphoreMaxConcurrentRequests;
    }

    public Boolean getFallbackEnabled() {
        return fallbackEnabled;
    }

    public void setFallbackEnabled(Boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public Integer getMetricsHealthSnapshotIntervalInMilliseconds() {
        return metricsHealthSnapshotIntervalInMilliseconds;
    }

    public void setMetricsHealthSnapshotIntervalInMilliseconds(Integer metricsHealthSnapshotIntervalInMilliseconds) {
        this.metricsHealthSnapshotIntervalInMilliseconds = metricsHealthSnapshotIntervalInMilliseconds;
    }

    public Integer getMetricsRollingPercentileBucketSize() {
        return metricsRollingPercentileBucketSize;
    }

    public void setMetricsRollingPercentileBucketSize(Integer metricsRollingPercentileBucketSize) {
        this.metricsRollingPercentileBucketSize = metricsRollingPercentileBucketSize;
    }

    public Boolean getMetricsRollingPercentileEnabled() {
        return metricsRollingPercentileEnabled;
    }

    public void setMetricsRollingPercentileEnabled(Boolean metricsRollingPercentileEnabled) {
        this.metricsRollingPercentileEnabled = metricsRollingPercentileEnabled;
    }

    public Integer getMetricsRollingPercentileWindowInMilliseconds() {
        return metricsRollingPercentileWindowInMilliseconds;
    }

    public void setMetricsRollingPercentileWindowInMilliseconds(Integer metricsRollingPercentileWindowInMilliseconds) {
        this.metricsRollingPercentileWindowInMilliseconds = metricsRollingPercentileWindowInMilliseconds;
    }

    public Integer getMetricsRollingPercentileWindowBuckets() {
        return metricsRollingPercentileWindowBuckets;
    }

    public void setMetricsRollingPercentileWindowBuckets(Integer metricsRollingPercentileWindowBuckets) {
        this.metricsRollingPercentileWindowBuckets = metricsRollingPercentileWindowBuckets;
    }

    public Integer getMetricsRollingStatisticalWindowInMilliseconds() {
        return metricsRollingStatisticalWindowInMilliseconds;
    }

    public void setMetricsRollingStatisticalWindowInMilliseconds(Integer metricsRollingStatisticalWindowInMilliseconds) {
        this.metricsRollingStatisticalWindowInMilliseconds = metricsRollingStatisticalWindowInMilliseconds;
    }

    public Integer getMetricsRollingStatisticalWindowBuckets() {
        return metricsRollingStatisticalWindowBuckets;
    }

    public void setMetricsRollingStatisticalWindowBuckets(Integer metricsRollingStatisticalWindowBuckets) {
        this.metricsRollingStatisticalWindowBuckets = metricsRollingStatisticalWindowBuckets;
    }

    public Boolean getRequestLogEnabled() {
        return requestLogEnabled;
    }

    public void setRequestLogEnabled(Boolean requestLogEnabled) {
        this.requestLogEnabled = requestLogEnabled;
    }

    public Integer getCorePoolSize() {
        return corePoolSize;
    }

    public void setCorePoolSize(Integer corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public Integer getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(Integer maximumSize) {
        this.maximumSize = maximumSize;
    }

    public Integer getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(Integer keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(Integer maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public Integer getQueueSizeRejectionThreshold() {
        return queueSizeRejectionThreshold;
    }

    public void setQueueSizeRejectionThreshold(Integer queueSizeRejectionThreshold) {
        this.queueSizeRejectionThreshold = queueSizeRejectionThreshold;
    }

    public Integer getThreadPoolRollingNumberStatisticalWindowInMilliseconds() {
        return threadPoolRollingNumberStatisticalWindowInMilliseconds;
    }

    public void setThreadPoolRollingNumberStatisticalWindowInMilliseconds(Integer threadPoolRollingNumberStatisticalWindowInMilliseconds) {
        this.threadPoolRollingNumberStatisticalWindowInMilliseconds = threadPoolRollingNumberStatisticalWindowInMilliseconds;
    }

    public Integer getThreadPoolRollingNumberStatisticalWindowBuckets() {
        return threadPoolRollingNumberStatisticalWindowBuckets;
    }

    public void setThreadPoolRollingNumberStatisticalWindowBuckets(Integer threadPoolRollingNumberStatisticalWindowBuckets) {
        this.threadPoolRollingNumberStatisticalWindowBuckets = threadPoolRollingNumberStatisticalWindowBuckets;
    }

    public Boolean getAllowMaximumSizeToDivergeFromCoreSize() {
        return allowMaximumSizeToDivergeFromCoreSize;
    }

    public void setAllowMaximumSizeToDivergeFromCoreSize(Boolean allowMaximumSizeToDivergeFromCoreSize) {
        this.allowMaximumSizeToDivergeFromCoreSize = allowMaximumSizeToDivergeFromCoreSize;
    }
}
