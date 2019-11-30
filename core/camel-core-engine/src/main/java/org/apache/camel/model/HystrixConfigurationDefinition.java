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

import java.util.concurrent.ThreadPoolExecutor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Hystrix Circuit Breaker EIP configuration
 */
@Metadata(label = "eip,routing,circuitbreaker")
@XmlRootElement(name = "hystrixConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class HystrixConfigurationDefinition extends HystrixConfigurationCommon {

    public static final String DEFAULT_GROUP_KEY = "CamelHystrix";

    @XmlTransient
    private CircuitBreakerDefinition parent;

    public HystrixConfigurationDefinition() {
    }

    public HystrixConfigurationDefinition(CircuitBreakerDefinition parent) {
        this.parent = parent;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Sets the group key to use. The default value is CamelHystrix.
     */
    public HystrixConfigurationDefinition groupKey(String groupKey) {
        setGroupKey(groupKey);
        return this;
    }

    /**
     * Sets the thread pool key to use. Will by default use the same value as
     * groupKey has been configured to use.
     */
    public HystrixConfigurationDefinition threadPoolKey(String threadPoolKey) {
        setThreadPoolKey(threadPoolKey);
        return this;
    }

    /**
     * Whether to use a HystrixCircuitBreaker or not. If false no
     * circuit-breaker logic will be used and all requests permitted.
     * <p>
     * This is similar in effect to circuitBreakerForceClosed() except that
     * continues tracking metrics and knowing whether it should be open/closed,
     * this property results in not even instantiating a circuit-breaker.
     */
    public HystrixConfigurationDefinition circuitBreakerEnabled(boolean circuitBreakerEnabled) {
        setCircuitBreakerEnabled(Boolean.toString(circuitBreakerEnabled));
        return this;
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
    public HystrixConfigurationDefinition circuitBreakerErrorThresholdPercentage(int circuitBreakerErrorThresholdPercentage) {
        setCircuitBreakerErrorThresholdPercentage(Integer.toString(circuitBreakerErrorThresholdPercentage));
        return this;
    }

    /**
     * If true the HystrixCircuitBreaker.allowRequest() will always return true
     * to allow requests regardless of the error percentage from
     * HystrixCommandMetrics.getHealthCounts().
     * <p>
     * The circuitBreakerForceOpen() property takes precedence so if it set to
     * true this property does nothing.
     */
    public HystrixConfigurationDefinition circuitBreakerForceClosed(boolean circuitBreakerForceClosed) {
        setCircuitBreakerForceClosed(Boolean.toString(circuitBreakerForceClosed));
        return this;
    }

    /**
     * If true the HystrixCircuitBreaker.allowRequest() will always return
     * false, causing the circuit to be open (tripped) and reject all requests.
     * <p>
     * This property takes precedence over circuitBreakerForceClosed();
     */
    public HystrixConfigurationDefinition circuitBreakerForceOpen(Boolean circuitBreakerForceOpen) {
        setCircuitBreakerForceOpen(Boolean.toString(circuitBreakerForceOpen));
        return this;
    }

    /**
     * Minimum number of requests in the
     * metricsRollingStatisticalWindowInMilliseconds() that must exist before
     * the HystrixCircuitBreaker will trip.
     * <p>
     * If below this number the circuit will not trip regardless of error
     * percentage.
     */
    public HystrixConfigurationDefinition circuitBreakerRequestVolumeThreshold(int circuitBreakerRequestVolumeThreshold) {
        setCircuitBreakerRequestVolumeThreshold(Integer.toString(circuitBreakerRequestVolumeThreshold));
        return this;
    }

    /**
     * The time in milliseconds after a HystrixCircuitBreaker trips open that it
     * should wait before trying requests again.
     */
    public HystrixConfigurationDefinition circuitBreakerSleepWindowInMilliseconds(int circuitBreakerSleepWindowInMilliseconds) {
        setCircuitBreakerSleepWindowInMilliseconds(Integer.toString(circuitBreakerSleepWindowInMilliseconds));
        return this;
    }

    /**
     * Number of concurrent requests permitted to HystrixCommand.run(). Requests
     * beyond the concurrent limit will be rejected.
     * <p>
     * Applicable only when executionIsolationStrategy is SEMAPHORE.
     */
    public HystrixConfigurationDefinition executionIsolationSemaphoreMaxConcurrentRequests(int executionIsolationSemaphoreMaxConcurrentRequests) {
        setExecutionIsolationSemaphoreMaxConcurrentRequests(Integer.toString(executionIsolationSemaphoreMaxConcurrentRequests));
        return this;
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
    public HystrixConfigurationDefinition executionIsolationStrategy(String executionIsolationStrategy) {
        setExecutionIsolationStrategy(executionIsolationStrategy);
        return this;
    }

    /**
     * Whether the execution thread should attempt an interrupt (using Future
     * cancel) when a thread times out.
     * <p>
     * Applicable only when executionIsolationStrategy() is set to THREAD.
     */
    public HystrixConfigurationDefinition executionIsolationThreadInterruptOnTimeout(boolean executionIsolationThreadInterruptOnTimeout) {
        setExecutionIsolationThreadInterruptOnTimeout(Boolean.toString(executionIsolationThreadInterruptOnTimeout));
        return this;
    }

    /**
     * Time in milliseconds at which point the command will timeout and halt
     * execution.
     * <p>
     * If executionIsolationThreadInterruptOnTimeout is true and the command is
     * thread-isolated, the executing thread will be interrupted. If the command
     * is semaphore-isolated and a HystrixObservableCommand, that command will
     * get unsubscribed.
     */
    public HystrixConfigurationDefinition executionTimeoutInMilliseconds(int executionTimeoutInMilliseconds) {
        setExecutionTimeoutInMilliseconds(Integer.toString(executionTimeoutInMilliseconds));
        return this;
    }

    /**
     * Whether the timeout mechanism is enabled for this command
     */
    public HystrixConfigurationDefinition executionTimeoutEnabled(boolean executionTimeoutEnabled) {
        setExecutionTimeoutEnabled(Boolean.toString(executionTimeoutEnabled));
        return this;
    }

    /**
     * Number of concurrent requests permitted to HystrixCommand.getFallback().
     * Requests beyond the concurrent limit will fail-fast and not attempt
     * retrieving a fallback.
     */
    public HystrixConfigurationDefinition fallbackIsolationSemaphoreMaxConcurrentRequests(int fallbackIsolationSemaphoreMaxConcurrentRequests) {
        setFallbackIsolationSemaphoreMaxConcurrentRequests(Integer.toString(fallbackIsolationSemaphoreMaxConcurrentRequests));
        return this;
    }

    /**
     * Whether HystrixCommand.getFallback() should be attempted when failure
     * occurs.
     */
    public HystrixConfigurationDefinition fallbackEnabled(boolean fallbackEnabled) {
        setFallbackEnabled(Boolean.toString(fallbackEnabled));
        return this;
    }

    /**
     * Time in milliseconds to wait between allowing health snapshots to be
     * taken that calculate success and error percentages and affect
     * HystrixCircuitBreaker.isOpen() status.
     * <p>
     * On high-volume circuits the continual calculation of error percentage can
     * become CPU intensive thus this controls how often it is calculated.
     */
    public HystrixConfigurationDefinition metricsHealthSnapshotIntervalInMilliseconds(int metricsHealthSnapshotIntervalInMilliseconds) {
        setMetricsHealthSnapshotIntervalInMilliseconds(Integer.toString(metricsHealthSnapshotIntervalInMilliseconds));
        return this;
    }

    /**
     * Maximum number of values stored in each bucket of the rolling percentile.
     * This is passed into HystrixRollingPercentile inside
     * HystrixCommandMetrics.
     */
    public HystrixConfigurationDefinition metricsRollingPercentileBucketSize(int metricsRollingPercentileBucketSize) {
        setMetricsRollingPercentileBucketSize(Integer.toString(metricsRollingPercentileBucketSize));
        return this;
    }

    /**
     * Whether percentile metrics should be captured using
     * HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public HystrixConfigurationDefinition metricsRollingPercentileEnabled(boolean metricsRollingPercentileEnabled) {
        setMetricsRollingPercentileEnabled(Boolean.toString(metricsRollingPercentileEnabled));
        return this;
    }

    /**
     * Duration of percentile rolling window in milliseconds. This is passed
     * into HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public HystrixConfigurationDefinition metricsRollingPercentileWindowInMilliseconds(int metricsRollingPercentileWindowInMilliseconds) {
        setMetricsRollingPercentileWindowInMilliseconds(Integer.toString(metricsRollingPercentileWindowInMilliseconds));
        return this;
    }

    /**
     * Number of buckets the rolling percentile window is broken into. This is
     * passed into HystrixRollingPercentile inside HystrixCommandMetrics.
     */
    public HystrixConfigurationDefinition metricsRollingPercentileWindowBuckets(int metricsRollingPercentileWindowBuckets) {
        setMetricsRollingPercentileWindowBuckets(Integer.toString(metricsRollingPercentileWindowBuckets));
        return this;
    }

    /**
     * This property sets the duration of the statistical rolling window, in
     * milliseconds. This is how long metrics are kept for the thread pool. The
     * window is divided into buckets and “rolls” by those increments.
     */
    public HystrixConfigurationDefinition metricsRollingStatisticalWindowInMilliseconds(int metricsRollingStatisticalWindowInMilliseconds) {
        setMetricsRollingStatisticalWindowInMilliseconds(Integer.toString(metricsRollingStatisticalWindowInMilliseconds));
        return this;
    }

    /**
     * Number of buckets the rolling statistical window is broken into. This is
     * passed into HystrixRollingNumber inside HystrixCommandMetrics.
     */
    public HystrixConfigurationDefinition metricsRollingStatisticalWindowBuckets(int metricsRollingStatisticalWindowBuckets) {
        setMetricsRollingStatisticalWindowBuckets(Integer.toString(metricsRollingStatisticalWindowBuckets));
        return this;
    }

    /**
     * Whether HystrixCommand execution and events should be logged to
     * HystrixRequestLog.
     */
    public HystrixConfigurationDefinition requestLogEnabled(boolean requestLogEnabled) {
        setRequestLogEnabled(Boolean.toString(requestLogEnabled));
        return this;
    }

    /**
     * Core thread-pool size.
     */
    public HystrixConfigurationDefinition corePoolSize(int corePoolSize) {
        setCorePoolSize(Integer.toString(corePoolSize));
        return this;
    }

    /**
     * Keep-alive time in minutes.
     */
    public HystrixConfigurationDefinition keepAliveTime(int keepAliveTime) {
        setKeepAliveTime(Integer.toString(keepAliveTime));
        return this;
    }

    /**
     * Max queue size. This should only affect the instantiation of the
     * thread-pool - it is not eligible to change a queue size on the fly.
     */
    public HystrixConfigurationDefinition maxQueueSize(int maxQueueSize) {
        setMaxQueueSize(Integer.toString(maxQueueSize));
        return this;
    }

    /**
     * Maximum thread-pool size that gets passed to
     * {@link ThreadPoolExecutor#setMaximumPoolSize(int)}. This is the maximum
     * amount of concurrency that can be supported without starting to reject
     * HystrixCommands. Please note that this setting only takes effect if you
     * also set allowMaximumSizeToDivergeFromCoreSize
     */
    public HystrixConfigurationDefinition maximumSize(int maximumSize) {
        setMaximumSize(Integer.toString(maximumSize));
        return this;
    }

    /**
     * Queue size rejection threshold is an artificial max size at which
     * rejections will occur even if maxQueueSize has not been reached. This is
     * done because the maxQueueSize of a blocking queue can not be dynamically
     * changed and we want to support dynamically changing the queue size that
     * affects rejections.
     * <p>
     * This is used by HystrixCommand when queuing a thread for execution.
     */
    public HystrixConfigurationDefinition queueSizeRejectionThreshold(int queueSizeRejectionThreshold) {
        setQueueSizeRejectionThreshold(Integer.toString(queueSizeRejectionThreshold));
        return this;
    }

    /**
     * Duration of statistical rolling window in milliseconds. This is passed
     * into HystrixRollingNumber inside each HystrixThreadPoolMetrics instance.
     */
    public HystrixConfigurationDefinition threadPoolRollingNumberStatisticalWindowInMilliseconds(int threadPoolRollingNumberStatisticalWindowInMilliseconds) {
        setThreadPoolRollingNumberStatisticalWindowInMilliseconds(Integer.toString(threadPoolRollingNumberStatisticalWindowInMilliseconds));
        return this;
    }

    /**
     * Number of buckets the rolling statistical window is broken into. This is
     * passed into HystrixRollingNumber inside each HystrixThreadPoolMetrics
     * instance.
     */
    public HystrixConfigurationDefinition threadPoolRollingNumberStatisticalWindowBuckets(int threadPoolRollingNumberStatisticalWindowBuckets) {
        setThreadPoolRollingNumberStatisticalWindowBuckets(Integer.toString(threadPoolRollingNumberStatisticalWindowBuckets));
        return this;
    }

    /**
     * Allows the configuration for maximumSize to take effect. That value can
     * then be equal to, or higher, than coreSize
     */
    public HystrixConfigurationDefinition allowMaximumSizeToDivergeFromCoreSize(boolean allowMaximumSizeToDivergeFromCoreSize) {
        setAllowMaximumSizeToDivergeFromCoreSize(Boolean.toString(allowMaximumSizeToDivergeFromCoreSize));
        return this;
    }

    /**
     * End of configuration.
     */
    public CircuitBreakerDefinition end() {
        return parent;
    }

}
