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
package org.apache.camel.model;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

import org.apache.camel.spi.Metadata;

@XmlAccessorType(XmlAccessType.FIELD)
public class HystrixConfigurationCommon extends IdentifiedType {

    @XmlAttribute
    @Metadata(defaultValue = "CamelHystrix")
    private String groupKey;
    @XmlAttribute
    @Metadata(defaultValue = "CamelHystrix")
    private String threadPoolKey;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "command", defaultValue = "true")
    private String circuitBreakerEnabled;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "50")
    private String circuitBreakerErrorThresholdPercentage;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "command", defaultValue = "false")
    private String circuitBreakerForceClosed;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "command", defaultValue = "false")
    private String circuitBreakerForceOpen;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "20")
    private String circuitBreakerRequestVolumeThreshold;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "5000")
    private String circuitBreakerSleepWindowInMilliseconds;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "20")
    private String executionIsolationSemaphoreMaxConcurrentRequests;
    @XmlAttribute
    @Metadata(label = "command", defaultValue = "THREAD", enums = "THREAD,SEMAPHORE")
    private String executionIsolationStrategy;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "command", defaultValue = "true")
    private String executionIsolationThreadInterruptOnTimeout;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "1000")
    private String executionTimeoutInMilliseconds;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "command", defaultValue = "true")
    private String executionTimeoutEnabled;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "10")
    private String fallbackIsolationSemaphoreMaxConcurrentRequests;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "command", defaultValue = "true")
    private String fallbackEnabled;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "500")
    private String metricsHealthSnapshotIntervalInMilliseconds;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "10")
    private String metricsRollingPercentileBucketSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "command", defaultValue = "true")
    private String metricsRollingPercentileEnabled;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "10000")
    private String metricsRollingPercentileWindowInMilliseconds;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "6")
    private String metricsRollingPercentileWindowBuckets;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "10000")
    private String metricsRollingStatisticalWindowInMilliseconds;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "command", defaultValue = "10")
    private String metricsRollingStatisticalWindowBuckets;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "command", defaultValue = "true")
    private String requestLogEnabled;

    // thread-pool

    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "threadpool", defaultValue = "10")
    private String corePoolSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "threadpool", defaultValue = "10")
    private String maximumSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "threadpool", defaultValue = "1")
    private String keepAliveTime;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "threadpool", defaultValue = "-1")
    private String maxQueueSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "threadpool", defaultValue = "5")
    private String queueSizeRejectionThreshold;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "threadpool", defaultValue = "10000")
    private String threadPoolRollingNumberStatisticalWindowInMilliseconds;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer", label = "threadpool", defaultValue = "10")
    private String threadPoolRollingNumberStatisticalWindowBuckets;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean", label = "threadpool", defaultValue = "false")
    private String allowMaximumSizeToDivergeFromCoreSize;

    // Getter/Setter
    // -------------------------------------------------------------------------

    public String getGroupKey() {
        return groupKey;
    }

    /**
     * Sets the group key to use. The default value is CamelHystrix.
     */
    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public String getThreadPoolKey() {
        return threadPoolKey;
    }

    /**
     * Sets the thread pool key to use. Will by default use the same value as
     * groupKey has been configured to use.
     */
    public void setThreadPoolKey(String threadPoolKey) {
        this.threadPoolKey = threadPoolKey;
    }

    public String getCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    /**
     * Whether to use a HystrixCircuitBreaker or not. If false no
     * circuit-breaker logic will be used and all requests permitted.
     * <p>
     * This is similar in effect to circuitBreakerForceClosed() except that
     * continues tracking metrics and knowing whether it should be open/closed,
     * this property results in not even instantiating a circuit-breaker.
     */
    public void setCircuitBreakerEnabled(String circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public String getCircuitBreakerErrorThresholdPercentage() {
        return circuitBreakerErrorThresholdPercentage;
    }

    /**
     * Error percentage threshold (as whole number such as 50) at which point
     * the circuit breaker will trip open and reject requests.
     * <p>
     * It will stay tripped for the duration defined in
     * circuitBreakerSleepWindowInMilliseconds;
     * <p>
     * The error percentage this is compared against comes from
     * HystrixCommandMetrics.getHealthCounts().
     */
    public void setCircuitBreakerErrorThresholdPercentage(String circuitBreakerErrorThresholdPercentage) {
        this.circuitBreakerErrorThresholdPercentage = circuitBreakerErrorThresholdPercentage;
    }

    public String getCircuitBreakerForceClosed() {
        return circuitBreakerForceClosed;
    }

    /**
     * If true the HystrixCircuitBreaker#allowRequest() will always return true
     * to allow requests regardless of the error percentage from
     * HystrixCommandMetrics.getHealthCounts().
     * <p>
     * The circuitBreakerForceOpen() property takes precedence so if it set to
     * true this property does nothing.
     */
    public void setCircuitBreakerForceClosed(String circuitBreakerForceClosed) {
        this.circuitBreakerForceClosed = circuitBreakerForceClosed;
    }

    public String getCircuitBreakerForceOpen() {
        return circuitBreakerForceOpen;
    }

    /**
     * If true the HystrixCircuitBreaker.allowRequest() will always return
     * false, causing the circuit to be open (tripped) and reject all requests.
     * <p>
     * This property takes precedence over circuitBreakerForceClosed();
     */
    public void setCircuitBreakerForceOpen(String circuitBreakerForceOpen) {
        this.circuitBreakerForceOpen = circuitBreakerForceOpen;
    }

    public String getCircuitBreakerRequestVolumeThreshold() {
        return circuitBreakerRequestVolumeThreshold;
    }

    /**
     * Minimum number of requests in the
     * metricsRollingStatisticalWindowInMilliseconds() that must exist before
     * the HystrixCircuitBreaker will trip.
     * <p>
     * If below this number the circuit will not trip regardless of error
     * percentage.
     */
    public void setCircuitBreakerRequestVolumeThreshold(String circuitBreakerRequestVolumeThreshold) {
        this.circuitBreakerRequestVolumeThreshold = circuitBreakerRequestVolumeThreshold;
    }

    public String getCircuitBreakerSleepWindowInMilliseconds() {
        return circuitBreakerSleepWindowInMilliseconds;
    }

    /**
     * The time in milliseconds after a HystrixCircuitBreaker trips open that it
     * should wait before trying requests again.
     */
    public void setCircuitBreakerSleepWindowInMilliseconds(String circuitBreakerSleepWindowInMilliseconds) {
        this.circuitBreakerSleepWindowInMilliseconds = circuitBreakerSleepWindowInMilliseconds;
    }

    public String getExecutionIsolationSemaphoreMaxConcurrentRequests() {
        return executionIsolationSemaphoreMaxConcurrentRequests;
    }

    /**
     * Number of concurrent requests permitted to HystrixCommand.run(). Requests
     * beyond the concurrent limit will be rejected.
     * <p>
     * Applicable only when executionIsolationStrategy == SEMAPHORE.
     */
    public void setExecutionIsolationSemaphoreMaxConcurrentRequests(String executionIsolationSemaphoreMaxConcurrentRequests) {
        this.executionIsolationSemaphoreMaxConcurrentRequests = executionIsolationSemaphoreMaxConcurrentRequests;
    }

    public String getExecutionIsolationStrategy() {
        return executionIsolationStrategy;
    }

    /**
     * What isolation strategy HystrixCommand.run() will be executed with.
     * <p>
     * If THREAD then it will be executed on a separate thread and concurrent
     * requests limited by the number of threads in the thread-pool.
     * <p>
     * If SEMAPHORE then it will be executed on the calling thread and
     * concurrent requests limited by the semaphore count.
     */
    public void setExecutionIsolationStrategy(String executionIsolationStrategy) {
        this.executionIsolationStrategy = executionIsolationStrategy;
    }

    public String getExecutionIsolationThreadInterruptOnTimeout() {
        return executionIsolationThreadInterruptOnTimeout;
    }

    /**
     * Whether the execution thread should attempt an interrupt (using
     * {@link Future#cancel}) when a thread times out.
     * <p>
     * Applicable only when executionIsolationStrategy() == THREAD.
     */
    public void setExecutionIsolationThreadInterruptOnTimeout(String executionIsolationThreadInterruptOnTimeout) {
        this.executionIsolationThreadInterruptOnTimeout = executionIsolationThreadInterruptOnTimeout;
    }

    public String getExecutionTimeoutInMilliseconds() {
        return executionTimeoutInMilliseconds;
    }

    /**
     * Time in milliseconds at which point the command will timeout and halt
     * execution.
     * <p>
     * If {@link #executionIsolationThreadInterruptOnTimeout} == true and the
     * command is thread-isolated, the executing thread will be interrupted. If
     * the command is semaphore-isolated and a HystrixObservableCommand, that
     * command will get unsubscribed.
     */
    public void setExecutionTimeoutInMilliseconds(String executionTimeoutInMilliseconds) {
        this.executionTimeoutInMilliseconds = executionTimeoutInMilliseconds;
    }

    public String getExecutionTimeoutEnabled() {
        return executionTimeoutEnabled;
    }

    /**
     * Whether the timeout mechanism is enabled for this command
     */
    public void setExecutionTimeoutEnabled(String executionTimeoutEnabled) {
        this.executionTimeoutEnabled = executionTimeoutEnabled;
    }

    public String getFallbackIsolationSemaphoreMaxConcurrentRequests() {
        return fallbackIsolationSemaphoreMaxConcurrentRequests;
    }

    /**
     * Number of concurrent requests permitted to HystrixCommand.getFallback().
     * Requests beyond the concurrent limit will fail-fast and not attempt
     * retrieving a fallback.
     */
    public void setFallbackIsolationSemaphoreMaxConcurrentRequests(String fallbackIsolationSemaphoreMaxConcurrentRequests) {
        this.fallbackIsolationSemaphoreMaxConcurrentRequests = fallbackIsolationSemaphoreMaxConcurrentRequests;
    }

    public String getFallbackEnabled() {
        return fallbackEnabled;
    }

    /**
     * Whether HystrixCommand.getFallback() should be attempted when failure
     * occurs.
     */
    public void setFallbackEnabled(String fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public String getMetricsHealthSnapshotIntervalInMilliseconds() {
        return metricsHealthSnapshotIntervalInMilliseconds;
    }

    /**
     * Time in milliseconds to wait between allowing health snapshots to be
     * taken that calculate success and error percentages and affect
     * HystrixCircuitBreaker.isOpen() status.
     * <p>
     * On high-volume circuits the continual calculation of error percentage can
     * become CPU intensive thus this controls how often it is calculated.
     */
    public void setMetricsHealthSnapshotIntervalInMilliseconds(String metricsHealthSnapshotIntervalInMilliseconds) {
        this.metricsHealthSnapshotIntervalInMilliseconds = metricsHealthSnapshotIntervalInMilliseconds;
    }

    public String getMetricsRollingPercentileBucketSize() {
        return metricsRollingPercentileBucketSize;
    }

    /**
     * Maximum number of values stored in each bucket of the rolling percentile.
     * This is passed into HystrixRollingPercentile inside
     * HystrixCommandMetrics.
     */
    public void setMetricsRollingPercentileBucketSize(String metricsRollingPercentileBucketSize) {
        this.metricsRollingPercentileBucketSize = metricsRollingPercentileBucketSize;
    }

    public String getMetricsRollingPercentileEnabled() {
        return metricsRollingPercentileEnabled;
    }

    /**
     * Whether percentile metrics should be captured using
     * HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public void setMetricsRollingPercentileEnabled(String metricsRollingPercentileEnabled) {
        this.metricsRollingPercentileEnabled = metricsRollingPercentileEnabled;
    }

    public String getMetricsRollingPercentileWindowInMilliseconds() {
        return metricsRollingPercentileWindowInMilliseconds;
    }

    /**
     * Duration of percentile rolling window in milliseconds. This is passed
     * into HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public void setMetricsRollingPercentileWindowInMilliseconds(String metricsRollingPercentileWindowInMilliseconds) {
        this.metricsRollingPercentileWindowInMilliseconds = metricsRollingPercentileWindowInMilliseconds;
    }

    public String getMetricsRollingPercentileWindowBuckets() {
        return metricsRollingPercentileWindowBuckets;
    }

    /**
     * Number of buckets the rolling percentile window is broken into. This is
     * passed into HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public void setMetricsRollingPercentileWindowBuckets(String metricsRollingPercentileWindowBuckets) {
        this.metricsRollingPercentileWindowBuckets = metricsRollingPercentileWindowBuckets;
    }

    public String getMetricsRollingStatisticalWindowInMilliseconds() {
        return metricsRollingStatisticalWindowInMilliseconds;
    }

    /**
     * This property sets the duration of the statistical rolling window, in
     * milliseconds. This is how long metrics are kept for the thread pool. The
     * window is divided into buckets and “rolls” by those increments.
     */
    public void setMetricsRollingStatisticalWindowInMilliseconds(String metricsRollingStatisticalWindowInMilliseconds) {
        this.metricsRollingStatisticalWindowInMilliseconds = metricsRollingStatisticalWindowInMilliseconds;
    }

    public String getMetricsRollingStatisticalWindowBuckets() {
        return metricsRollingStatisticalWindowBuckets;
    }

    /**
     * Number of buckets the rolling statistical window is broken into. This is
     * passed into HystrixRollingNumber inside HystrixCommandMetrics.
     */
    public void setMetricsRollingStatisticalWindowBuckets(String metricsRollingStatisticalWindowBuckets) {
        this.metricsRollingStatisticalWindowBuckets = metricsRollingStatisticalWindowBuckets;
    }

    public String getRequestLogEnabled() {
        return requestLogEnabled;
    }

    /**
     * Whether HystrixCommand execution and events should be logged to
     * HystrixRequestLog.
     */
    public void setRequestLogEnabled(String requestLogEnabled) {
        this.requestLogEnabled = requestLogEnabled;
    }

    public String getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Core thread-pool size that gets passed to
     * {@link java.util.concurrent.ThreadPoolExecutor#setCorePoolSize(int)}
     */
    public void setCorePoolSize(String corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public String getMaximumSize() {
        return maximumSize;
    }

    /**
     * Maximum thread-pool size that gets passed to
     * {@link ThreadPoolExecutor#setMaximumPoolSize(int)}. This is the maximum
     * amount of concurrency that can be supported without starting to reject
     * HystrixCommands. Please note that this setting only takes effect if you
     * also set allowMaximumSizeToDivergeFromCoreSize
     */
    public void setMaximumSize(String maximumSize) {
        this.maximumSize = maximumSize;
    }

    public String getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Keep-alive time in minutes that gets passed to
     * {@link ThreadPoolExecutor#setKeepAliveTime(long, TimeUnit)}
     */
    public void setKeepAliveTime(String keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public String getMaxQueueSize() {
        return maxQueueSize;
    }

    /**
     * Max queue size that gets passed to {@link BlockingQueue} in
     * HystrixConcurrencyStrategy.getBlockingQueue(int) This should only affect
     * the instantiation of a threadpool - it is not eliglible to change a queue
     * size on the fly. For that, use queueSizeRejectionThreshold().
     */
    public void setMaxQueueSize(String maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public String getQueueSizeRejectionThreshold() {
        return queueSizeRejectionThreshold;
    }

    /**
     * Queue size rejection threshold is an artificial "max" size at which
     * rejections will occur even if {@link #maxQueueSize} has not been reached.
     * This is done because the {@link #maxQueueSize} of a {@link BlockingQueue}
     * can not be dynamically changed and we want to support dynamically
     * changing the queue size that affects rejections.
     * <p>
     * This is used by HystrixCommand when queuing a thread for execution.
     */
    public void setQueueSizeRejectionThreshold(String queueSizeRejectionThreshold) {
        this.queueSizeRejectionThreshold = queueSizeRejectionThreshold;
    }

    public String getThreadPoolRollingNumberStatisticalWindowInMilliseconds() {
        return threadPoolRollingNumberStatisticalWindowInMilliseconds;
    }

    /**
     * Duration of statistical rolling window in milliseconds. This is passed
     * into HystrixRollingNumber inside each HystrixThreadPoolMetrics instance.
     */
    public void setThreadPoolRollingNumberStatisticalWindowInMilliseconds(String threadPoolRollingNumberStatisticalWindowInMilliseconds) {
        this.threadPoolRollingNumberStatisticalWindowInMilliseconds = threadPoolRollingNumberStatisticalWindowInMilliseconds;
    }

    public String getThreadPoolRollingNumberStatisticalWindowBuckets() {
        return threadPoolRollingNumberStatisticalWindowBuckets;
    }

    /**
     * Number of buckets the rolling statistical window is broken into. This is
     * passed into HystrixRollingNumber inside each HystrixThreadPoolMetrics
     * instance.
     */
    public void setThreadPoolRollingNumberStatisticalWindowBuckets(String threadPoolRollingNumberStatisticalWindowBuckets) {
        this.threadPoolRollingNumberStatisticalWindowBuckets = threadPoolRollingNumberStatisticalWindowBuckets;
    }

    public String getAllowMaximumSizeToDivergeFromCoreSize() {
        return allowMaximumSizeToDivergeFromCoreSize;
    }

    /**
     * Allows the configuration for maximumSize to take effect. That value can
     * then be equal to, or higher, than coreSize
     */
    public void setAllowMaximumSizeToDivergeFromCoreSize(String allowMaximumSizeToDivergeFromCoreSize) {
        this.allowMaximumSizeToDivergeFromCoreSize = allowMaximumSizeToDivergeFromCoreSize;
    }
}
