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
    @Metadata(label = "command", defaultValue = "false")
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
     * Sets the thread pool key to use. Will by default use the same value as groupKey has been configured to use.
     */
    public void setThreadPoolKey(String threadPoolKey) {
        this.threadPoolKey = threadPoolKey;
    }

    public Boolean getCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    /**
     * Whether to use a HystrixCircuitBreaker or not. If false no circuit-breaker logic will be used and all requests permitted.
     * <p>
     * This is similar in effect to circuitBreakerForceClosed() except that continues tracking metrics and knowing whether it
     * should be open/closed, this property results in not even instantiating a circuit-breaker.
     */
    public void setCircuitBreakerEnabled(Boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public Integer getCircuitBreakerErrorThresholdPercentage() {
        return circuitBreakerErrorThresholdPercentage;
    }

    /**
     * Error percentage threshold (as whole number such as 50) at which point the circuit breaker will trip open and reject requests.
     * <p>
     * It will stay tripped for the duration defined in circuitBreakerSleepWindowInMilliseconds;
     * <p>
     * The error percentage this is compared against comes from HystrixCommandMetrics.getHealthCounts().
     */
    public void setCircuitBreakerErrorThresholdPercentage(Integer circuitBreakerErrorThresholdPercentage) {
        this.circuitBreakerErrorThresholdPercentage = circuitBreakerErrorThresholdPercentage;
    }

    public Boolean getCircuitBreakerForceClosed() {
        return circuitBreakerForceClosed;
    }

    /**
     * If true the HystrixCircuitBreaker#allowRequest() will always return true to allow requests regardless of
     * the error percentage from HystrixCommandMetrics.getHealthCounts().
     * <p>
     * The circuitBreakerForceOpen() property takes precedence so if it set to true this property does nothing.
     */
    public void setCircuitBreakerForceClosed(Boolean circuitBreakerForceClosed) {
        this.circuitBreakerForceClosed = circuitBreakerForceClosed;
    }

    public Boolean getCircuitBreakerForceOpen() {
        return circuitBreakerForceOpen;
    }

    /**
     * If true the HystrixCircuitBreaker.allowRequest() will always return false, causing the circuit to be open (tripped) and reject all requests.
     * <p>
     * This property takes precedence over circuitBreakerForceClosed();
     */
    public void setCircuitBreakerForceOpen(Boolean circuitBreakerForceOpen) {
        this.circuitBreakerForceOpen = circuitBreakerForceOpen;
    }

    public Integer getCircuitBreakerRequestVolumeThreshold() {
        return circuitBreakerRequestVolumeThreshold;
    }

    /**
     * Minimum number of requests in the metricsRollingStatisticalWindowInMilliseconds() that must exist before the HystrixCircuitBreaker will trip.
     * <p>
     * If below this number the circuit will not trip regardless of error percentage.
     */
    public void setCircuitBreakerRequestVolumeThreshold(Integer circuitBreakerRequestVolumeThreshold) {
        this.circuitBreakerRequestVolumeThreshold = circuitBreakerRequestVolumeThreshold;
    }

    public Integer getCircuitBreakerSleepWindowInMilliseconds() {
        return circuitBreakerSleepWindowInMilliseconds;
    }

    /**
     * The time in milliseconds after a HystrixCircuitBreaker trips open that it should wait before trying requests again.
     */
    public void setCircuitBreakerSleepWindowInMilliseconds(Integer circuitBreakerSleepWindowInMilliseconds) {
        this.circuitBreakerSleepWindowInMilliseconds = circuitBreakerSleepWindowInMilliseconds;
    }

    public Integer getExecutionIsolationSemaphoreMaxConcurrentRequests() {
        return executionIsolationSemaphoreMaxConcurrentRequests;
    }

    /**
     * Number of concurrent requests permitted to HystrixCommand.run(). Requests beyond the concurrent limit will be rejected.
     * <p>
     * Applicable only when executionIsolationStrategy == SEMAPHORE.
     */
    public void setExecutionIsolationSemaphoreMaxConcurrentRequests(Integer executionIsolationSemaphoreMaxConcurrentRequests) {
        this.executionIsolationSemaphoreMaxConcurrentRequests = executionIsolationSemaphoreMaxConcurrentRequests;
    }

    public String getExecutionIsolationStrategy() {
        return executionIsolationStrategy;
    }

    /**
     * What isolation strategy HystrixCommand.run() will be executed with.
     * <p>
     * If THREAD then it will be executed on a separate thread and concurrent requests limited by the number of threads in the thread-pool.
     * <p>
     * If SEMAPHORE then it will be executed on the calling thread and concurrent requests limited by the semaphore count.
     */
    public void setExecutionIsolationStrategy(String executionIsolationStrategy) {
        this.executionIsolationStrategy = executionIsolationStrategy;
    }

    public Boolean getExecutionIsolationThreadInterruptOnTimeout() {
        return executionIsolationThreadInterruptOnTimeout;
    }

    /**
     * Whether the execution thread should attempt an interrupt (using {@link Future#cancel}) when a thread times out.
     * <p>
     * Applicable only when executionIsolationStrategy() == THREAD.
     */
    public void setExecutionIsolationThreadInterruptOnTimeout(Boolean executionIsolationThreadInterruptOnTimeout) {
        this.executionIsolationThreadInterruptOnTimeout = executionIsolationThreadInterruptOnTimeout;
    }

    public Integer getExecutionTimeoutInMilliseconds() {
        return executionTimeoutInMilliseconds;
    }

    /**
     * Time in milliseconds at which point the command will timeout and halt execution.
     * <p>
     * If {@link #executionIsolationThreadInterruptOnTimeout} == true and the command is thread-isolated, the executing thread will be interrupted.
     * If the command is semaphore-isolated and a HystrixObservableCommand, that command will get unsubscribed.
     */
    public void setExecutionTimeoutInMilliseconds(Integer executionTimeoutInMilliseconds) {
        this.executionTimeoutInMilliseconds = executionTimeoutInMilliseconds;
    }

    public Boolean getExecutionTimeoutEnabled() {
        return executionTimeoutEnabled;
    }
    /**
     * Whether the timeout mechanism is enabled for this command
     */
    public void setExecutionTimeoutEnabled(Boolean executionTimeoutEnabled) {
        this.executionTimeoutEnabled = executionTimeoutEnabled;
    }

    public Integer getFallbackIsolationSemaphoreMaxConcurrentRequests() {
        return fallbackIsolationSemaphoreMaxConcurrentRequests;
    }

    /**
     * Number of concurrent requests permitted to HystrixCommand.getFallback().
     * Requests beyond the concurrent limit will fail-fast and not attempt retrieving a fallback.
     */
    public void setFallbackIsolationSemaphoreMaxConcurrentRequests(Integer fallbackIsolationSemaphoreMaxConcurrentRequests) {
        this.fallbackIsolationSemaphoreMaxConcurrentRequests = fallbackIsolationSemaphoreMaxConcurrentRequests;
    }

    public Boolean getFallbackEnabled() {
        return fallbackEnabled;
    }

    /**
     * Whether HystrixCommand.getFallback() should be attempted when failure occurs.
     */
    public void setFallbackEnabled(Boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public Integer getMetricsHealthSnapshotIntervalInMilliseconds() {
        return metricsHealthSnapshotIntervalInMilliseconds;
    }

    /**
     * Time in milliseconds to wait between allowing health snapshots to be taken that calculate success and error
     * percentages and affect HystrixCircuitBreaker.isOpen() status.
     * <p>
     * On high-volume circuits the continual calculation of error percentage can become CPU intensive thus this controls how often it is calculated.
     */
    public void setMetricsHealthSnapshotIntervalInMilliseconds(Integer metricsHealthSnapshotIntervalInMilliseconds) {
        this.metricsHealthSnapshotIntervalInMilliseconds = metricsHealthSnapshotIntervalInMilliseconds;
    }

    public Integer getMetricsRollingPercentileBucketSize() {
        return metricsRollingPercentileBucketSize;
    }

    /**
     * Maximum number of values stored in each bucket of the rolling percentile.
     * This is passed into HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public void setMetricsRollingPercentileBucketSize(Integer metricsRollingPercentileBucketSize) {
        this.metricsRollingPercentileBucketSize = metricsRollingPercentileBucketSize;
    }

    public Boolean getMetricsRollingPercentileEnabled() {
        return metricsRollingPercentileEnabled;
    }

    /**
     * Whether percentile metrics should be captured using HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public void setMetricsRollingPercentileEnabled(Boolean metricsRollingPercentileEnabled) {
        this.metricsRollingPercentileEnabled = metricsRollingPercentileEnabled;
    }

    public Integer getMetricsRollingPercentileWindowInMilliseconds() {
        return metricsRollingPercentileWindowInMilliseconds;
    }

    /**
     * Duration of percentile rolling window in milliseconds.
     * This is passed into HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public void setMetricsRollingPercentileWindowInMilliseconds(Integer metricsRollingPercentileWindowInMilliseconds) {
        this.metricsRollingPercentileWindowInMilliseconds = metricsRollingPercentileWindowInMilliseconds;
    }

    public Integer getMetricsRollingPercentileWindowBuckets() {
        return metricsRollingPercentileWindowBuckets;
    }

    /**
     * Number of buckets the rolling percentile window is broken into.
     * This is passed into HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public void setMetricsRollingPercentileWindowBuckets(Integer metricsRollingPercentileWindowBuckets) {
        this.metricsRollingPercentileWindowBuckets = metricsRollingPercentileWindowBuckets;
    }

    public Integer getMetricsRollingStatisticalWindowInMilliseconds() {
        return metricsRollingStatisticalWindowInMilliseconds;
    }

    /**
     * This property sets the duration of the statistical rolling window, in milliseconds. This is how long metrics are kept for the thread pool.
     *
     * The window is divided into buckets and “rolls” by those increments.
     */
    public void setMetricsRollingStatisticalWindowInMilliseconds(Integer metricsRollingStatisticalWindowInMilliseconds) {
        this.metricsRollingStatisticalWindowInMilliseconds = metricsRollingStatisticalWindowInMilliseconds;
    }

    public Integer getMetricsRollingStatisticalWindowBuckets() {
        return metricsRollingStatisticalWindowBuckets;
    }

    /**
     * Number of buckets the rolling statistical window is broken into.
     * This is passed into HystrixRollingNumber inside HystrixCommandMetrics.
     */
    public void setMetricsRollingStatisticalWindowBuckets(Integer metricsRollingStatisticalWindowBuckets) {
        this.metricsRollingStatisticalWindowBuckets = metricsRollingStatisticalWindowBuckets;
    }

    public Boolean getRequestLogEnabled() {
        return requestLogEnabled;
    }

    /**
     * Whether HystrixCommand execution and events should be logged to HystrixRequestLog.
     */
    public void setRequestLogEnabled(Boolean requestLogEnabled) {
        this.requestLogEnabled = requestLogEnabled;
    }

    public Integer getCorePoolSize() {
        return corePoolSize;
    }

    /**
     * Core thread-pool size that gets passed to {@link java.util.concurrent.ThreadPoolExecutor#setCorePoolSize(int)}
     */
    public void setCorePoolSize(Integer corePoolSize) {
        this.corePoolSize = corePoolSize;
    }

    public Integer getMaximumSize() {
        return maximumSize;
    }

    /**
     * Maximum thread-pool size that gets passed to {@link ThreadPoolExecutor#setMaximumPoolSize(int)}.
     * This is the maximum amount of concurrency that can be supported without starting to reject HystrixCommands.
     * Please note that this setting only takes effect if you also set allowMaximumSizeToDivergeFromCoreSize
     */
    public void setMaximumSize(Integer maximumSize) {
        this.maximumSize = maximumSize;
    }

    public Integer getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Keep-alive time in minutes that gets passed to {@link ThreadPoolExecutor#setKeepAliveTime(long, TimeUnit)}
     */
    public void setKeepAliveTime(Integer keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    /**
     * Max queue size that gets passed to {@link BlockingQueue} in HystrixConcurrencyStrategy.getBlockingQueue(int)
     *
     * This should only affect the instantiation of a threadpool - it is not eliglible to change a queue size on the fly.
     * For that, use queueSizeRejectionThreshold().
     */
    public void setMaxQueueSize(Integer maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public Integer getQueueSizeRejectionThreshold() {
        return queueSizeRejectionThreshold;
    }

    /**
     * Queue size rejection threshold is an artificial "max" size at which rejections will occur even
     * if {@link #maxQueueSize} has not been reached. This is done because the {@link #maxQueueSize}
     * of a {@link BlockingQueue} can not be dynamically changed and we want to support dynamically
     * changing the queue size that affects rejections.
     * <p>
     * This is used by HystrixCommand when queuing a thread for execution.
     */
    public void setQueueSizeRejectionThreshold(Integer queueSizeRejectionThreshold) {
        this.queueSizeRejectionThreshold = queueSizeRejectionThreshold;
    }

    public Integer getThreadPoolRollingNumberStatisticalWindowInMilliseconds() {
        return threadPoolRollingNumberStatisticalWindowInMilliseconds;
    }

    /**
     * Duration of statistical rolling window in milliseconds.
     * This is passed into HystrixRollingNumber inside each HystrixThreadPoolMetrics instance.
     */
    public void setThreadPoolRollingNumberStatisticalWindowInMilliseconds(Integer threadPoolRollingNumberStatisticalWindowInMilliseconds) {
        this.threadPoolRollingNumberStatisticalWindowInMilliseconds = threadPoolRollingNumberStatisticalWindowInMilliseconds;
    }

    public Integer getThreadPoolRollingNumberStatisticalWindowBuckets() {
        return threadPoolRollingNumberStatisticalWindowBuckets;
    }

    /**
     * Number of buckets the rolling statistical window is broken into.
     * This is passed into HystrixRollingNumber inside each HystrixThreadPoolMetrics instance.
     */
    public void setThreadPoolRollingNumberStatisticalWindowBuckets(Integer threadPoolRollingNumberStatisticalWindowBuckets) {
        this.threadPoolRollingNumberStatisticalWindowBuckets = threadPoolRollingNumberStatisticalWindowBuckets;
    }

    public Boolean getAllowMaximumSizeToDivergeFromCoreSize() {
        return allowMaximumSizeToDivergeFromCoreSize;
    }

    /**
     * Allows the configuration for maximumSize to take effect. That value can then be equal to, or higher, than coreSize
     */
    public void setAllowMaximumSizeToDivergeFromCoreSize(Boolean allowMaximumSizeToDivergeFromCoreSize) {
        this.allowMaximumSizeToDivergeFromCoreSize = allowMaximumSizeToDivergeFromCoreSize;
    }
}
