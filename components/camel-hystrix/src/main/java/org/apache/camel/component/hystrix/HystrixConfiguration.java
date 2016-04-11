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
package org.apache.camel.component.hystrix;

import org.apache.camel.Expression;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class HystrixConfiguration {

    /**
     * Specifies the groupKey to use
     */
    @UriPath
    @Metadata(required = "true")
    private String groupKey;

    /**
     * Specifies the commandKey to use
     */
    @UriParam(label = "producer")
    private String commandKey;

    /**
     * Specifies the threadPoolKey to use
     */
    @UriParam(label = "producer")
    private String threadPoolKey;

    /**
     * Specifies the cacheKeyExpression to use
     */
    @UriParam(label = "producer")
    private Expression cacheKeyExpression;


    /**
     * Specifies the initializeRequestContext to use
     */
    @UriParam(label = "producer")
    private Boolean initializeRequestContext;

    /**
     * Specifies the endpoint to use
     */
    @UriParam(label = "producer")
    private String runEndpointId;

    /**
     * Specifies the fallbackEndpointId to use
     */
    @UriParam(label = "producer")
    private String fallbackEndpointId;

    private Integer coreSize;
    private Integer keepAliveTime;
    private Integer maxQueueSize;
    private Integer queueSizeRejectionThreshold;
    private Integer threadPoolMetricsRollingStatisticalWindowInMilliseconds;
    private Integer threadPoolMetricsRollingStatisticalWindowBuckets;

    private Boolean circuitBreakerEnabled;
    private Integer circuitBreakerErrorThresholdPercentage;
    private Boolean circuitBreakerForceClosed;
    private Boolean circuitBreakerForceOpen;
    private Integer circuitBreakerRequestVolumeThreshold;
    private Integer circuitBreakerSleepWindowInMilliseconds;
    private Integer executionIsolationSemaphoreMaxConcurrentRequests;

    /**
     * Specifies the isolation strategy (thread or semaphore) to use
     */
    @UriParam(label = "producer", defaultValue = "THREAD")
    private String  executionIsolationStrategy;
    private Boolean executionIsolationThreadInterruptOnTimeout;
    private Integer executionTimeoutInMilliseconds;
    private Boolean executionTimeoutEnabled;
    private Integer fallbackIsolationSemaphoreMaxConcurrentRequests;
    private Boolean fallbackEnabled;
    private Integer metricsHealthSnapshotIntervalInMilliseconds;
    private Integer metricsRollingPercentileBucketSize;
    private Boolean metricsRollingPercentileEnabled;
    private Integer metricsRollingPercentileWindowInMilliseconds;
    private Integer metricsRollingPercentileWindowBuckets;
    private Integer metricsRollingStatisticalWindowInMilliseconds;
    private Integer metricsRollingStatisticalWindowBuckets;
    private Boolean requestCacheEnabled;
    private Boolean requestLogEnabled;

    public String getRunEndpointId() {
        return runEndpointId;
    }

    public void setRunEndpointId(String runEndpointId) {
        this.runEndpointId = runEndpointId;
    }

    public String getFallbackEndpointId() {
        return fallbackEndpointId;
    }

    public void setFallbackEndpointId(String fallbackEndpointId) {
        this.fallbackEndpointId = fallbackEndpointId;
    }

    public Expression getCacheKeyExpression() {
        return cacheKeyExpression;
    }

    public void setCacheKeyExpression(Expression cacheKeyExpression) {
        this.cacheKeyExpression = cacheKeyExpression;
    }


    public Boolean getInitializeRequestContext() {
        return initializeRequestContext;
    }

    public void setInitializeRequestContext(Boolean initializeRequestContext) {
        this.initializeRequestContext = initializeRequestContext;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public String getCommandKey() {
        return commandKey;
    }

    public void setCommandKey(String commandKey) {
        this.commandKey = commandKey;
    }

    public String getThreadPoolKey() {
        return threadPoolKey;
    }

    public void setThreadPoolKey(String threadPoolKey) {
        this.threadPoolKey = threadPoolKey;
    }

    public Integer getCoreSize() {
        return coreSize;
    }

    public void setCoreSize(Integer coreSize) {
        this.coreSize = coreSize;
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

    public Integer getThreadPoolMetricsRollingStatisticalWindowInMilliseconds() {
        return threadPoolMetricsRollingStatisticalWindowInMilliseconds;
    }

    public void setThreadPoolMetricsRollingStatisticalWindowInMilliseconds(Integer threadPoolMetricsRollingStatisticalWindowInMilliseconds) {
        this.threadPoolMetricsRollingStatisticalWindowInMilliseconds = threadPoolMetricsRollingStatisticalWindowInMilliseconds;
    }

    public Integer getThreadPoolMetricsRollingStatisticalWindowBuckets() {
        return threadPoolMetricsRollingStatisticalWindowBuckets;
    }

    public void setThreadPoolMetricsRollingStatisticalWindowBuckets(Integer threadPoolMetricsRollingStatisticalWindowBuckets) {
        this.threadPoolMetricsRollingStatisticalWindowBuckets = threadPoolMetricsRollingStatisticalWindowBuckets;
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

    public Boolean getRequestCacheEnabled() {
        return requestCacheEnabled;
    }

    public void setRequestCacheEnabled(Boolean requestCacheEnabled) {
        this.requestCacheEnabled = requestCacheEnabled;
    }

    public Boolean getRequestLogEnabled() {
        return requestLogEnabled;
    }

    public void setRequestLogEnabled(Boolean requestLogEnabled) {
        this.requestLogEnabled = requestLogEnabled;
    }
}
