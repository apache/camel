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

import java.util.concurrent.Future;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixRequestCache;
import com.netflix.hystrix.HystrixRequestLog;
import com.netflix.hystrix.HystrixThreadPool;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.strategy.concurrency.HystrixRequestVariableDefault;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingPercentile;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class HystrixConfiguration {

    @UriPath
    @Metadata(required = "true")
    private String groupKey;
    @UriParam(defaultValue = "CamelHystrixCommand")
    private String commandKey;
    @UriParam
    @Metadata(required = "true")
    private String runEndpoint;
    @UriParam
    private String cacheKey;
    @UriParam(defaultValue = "true")
    private Boolean requestCacheEnabled;
    @UriParam
    private String fallbackEndpoint;
    @UriParam(defaultValue = "true")
    private Boolean fallbackEnabled;
    @UriParam
    private boolean metricsEnabled;
    @UriParam(label = "threadpool")
    private String threadPoolKey;
    @UriParam(label = "threadpool")
    private Boolean initializeRequestContext;
    @UriParam(label = "threadpool", defaultValue = "10")
    private Integer coreSize;
    @UriParam(label = "threadpool", defaultValue = "1")
    private Integer keepAliveTime;
    @UriParam(label = "threadpool", defaultValue = "-1")
    private Integer maxQueueSize;
    @UriParam(label = "threadpool", defaultValue = "5")
    private Integer queueSizeRejectionThreshold;
    @UriParam(label = "threadpool", defaultValue = "10000")
    private Integer threadPoolMetricsRollingStatisticalWindowInMilliseconds;
    @UriParam(label = "threadpool", defaultValue = "10")
    private Integer threadPoolMetricsRollingStatisticalWindowBuckets;
    @UriParam(label = "circuitbreaker", defaultValue = "true")
    private Boolean circuitBreakerEnabled;
    @UriParam(label = "circuitbreaker", defaultValue = "50")
    private Integer circuitBreakerErrorThresholdPercentage;
    @UriParam(label = "circuitbreaker")
    private Boolean circuitBreakerForceClosed;
    @UriParam(label = "circuitbreaker")
    private Boolean circuitBreakerForceOpen;
    @UriParam(label = "circuitbreaker", defaultValue = "20")
    private Integer circuitBreakerRequestVolumeThreshold;
    @UriParam(label = "circuitbreaker", defaultValue = "5000")
    private Integer circuitBreakerSleepWindowInMilliseconds;
    @UriParam(label = "circuitbreaker", defaultValue = "10")
    private Integer executionIsolationSemaphoreMaxConcurrentRequests;
    @UriParam(label = "circuitbreaker", defaultValue = "THREAD", enums = "THREAD,SEMAPHORE")
    private String executionIsolationStrategy;
    @UriParam(label = "circuitbreaker", defaultValue = "true")
    private Boolean executionIsolationThreadInterruptOnTimeout;
    @UriParam(label = "circuitbreaker", defaultValue = "1000")
    private Integer executionTimeoutInMilliseconds;
    @UriParam(label = "circuitbreaker", defaultValue = "true")
    private Boolean executionTimeoutEnabled;
    @UriParam(label = "circuitbreaker", defaultValue = "10")
    private Integer fallbackIsolationSemaphoreMaxConcurrentRequests;
    @UriParam(label = "monitoring", defaultValue = "500")
    private Integer metricsHealthSnapshotIntervalInMilliseconds;
    @UriParam(label = "monitoring", defaultValue = "100")
    private Integer metricsRollingPercentileBucketSize;
    @UriParam(label = "monitoring", defaultValue = "true")
    private Boolean metricsRollingPercentileEnabled;
    @UriParam(label = "monitoring", defaultValue = "60000")
    private Integer metricsRollingPercentileWindowInMilliseconds;
    @UriParam(label = "monitoring", defaultValue = "6")
    private Integer metricsRollingPercentileWindowBuckets;
    @UriParam(label = "monitoring", defaultValue = "10000")
    private Integer metricsRollingStatisticalWindowInMilliseconds;
    @UriParam(label = "monitoring", defaultValue = "10")
    private Integer metricsRollingStatisticalWindowBuckets;
    @UriParam(label = "monitoring", defaultValue = "true")
    private Boolean requestLogEnabled;

    public String getRunEndpoint() {
        return runEndpoint;
    }

    /**
     * Specifies the endpoint to use.
     * Specify either an url or name of existing endpoint.
     */
    public void setRunEndpoint(String runEndpoint) {
        this.runEndpoint = runEndpoint;
    }

    public String getFallbackEndpoint() {
        return fallbackEndpoint;
    }

    /**
     * Specifies the fallback endpoint to use
     * Specify either an url or name of existing endpoint.
     */
    public void setFallbackEndpoint(String fallbackEndpoint) {
        this.fallbackEndpoint = fallbackEndpoint;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    /**
     * Specifies the cache key to use.
     * Uses the simple language as the expression. But you can refer to an existing expression using # lookup.
     */
    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public Boolean getInitializeRequestContext() {
        return initializeRequestContext;
    }

    /**
     * Call this at the beginning of each request (from parent thread)
     * to initialize the underlying context so that {@link HystrixRequestVariableDefault} can be used on any children threads and be accessible from
     * the parent thread.
     */
    public void setInitializeRequestContext(Boolean initializeRequestContext) {
        this.initializeRequestContext = initializeRequestContext;
    }

    public String getGroupKey() {
        return groupKey;
    }

    /**
     * Specifies the group key to use
     */
    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public String getCommandKey() {
        return commandKey;
    }

    /**
     * Used to identify a HystrixCommand instance for statistics, circuit-breaker, properties, etc.
     * By default this will be derived from the instance class name.
     */
    public void setCommandKey(String commandKey) {
        this.commandKey = commandKey;
    }

    public String getThreadPoolKey() {
        return threadPoolKey;
    }

    /**
     * Used to define which thread-pool this command should run in. By default this is derived from the HystrixCommandGroupKey.
     */
    public void setThreadPoolKey(String threadPoolKey) {
        this.threadPoolKey = threadPoolKey;
    }

    public Integer getCoreSize() {
        return coreSize;
    }

    /**
     * This property sets the core thread-pool size. This is the maximum number of HystrixCommands that can execute concurrently.
     */
    public void setCoreSize(Integer coreSize) {
        this.coreSize = coreSize;
    }

    public Integer getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * This property sets the keep-alive time, in minutes.
     */
    public void setKeepAliveTime(Integer keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    /**
     * This property sets the maximum queue size of the BlockingQueue implementation.
     */
    public void setMaxQueueSize(Integer maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public Integer getQueueSizeRejectionThreshold() {
        return queueSizeRejectionThreshold;
    }

    /**
     * This property sets the queue size rejection threshold — an artificial maximum queue size at which rejections will occur even if maxQueueSize has not been reached.
     */
    public void setQueueSizeRejectionThreshold(Integer queueSizeRejectionThreshold) {
        this.queueSizeRejectionThreshold = queueSizeRejectionThreshold;
    }

    public Integer getThreadPoolMetricsRollingStatisticalWindowInMilliseconds() {
        return threadPoolMetricsRollingStatisticalWindowInMilliseconds;
    }

    /**
     * This property sets the duration of the statistical rolling window, in milliseconds. This is how long metrics are kept for the thread pool.
     */
    public void setThreadPoolMetricsRollingStatisticalWindowInMilliseconds(Integer threadPoolMetricsRollingStatisticalWindowInMilliseconds) {
        this.threadPoolMetricsRollingStatisticalWindowInMilliseconds = threadPoolMetricsRollingStatisticalWindowInMilliseconds;
    }

    public Integer getThreadPoolMetricsRollingStatisticalWindowBuckets() {
        return threadPoolMetricsRollingStatisticalWindowBuckets;
    }

    /**
     * This property sets the number of buckets the rolling statistical window is divided into.
     */
    public void setThreadPoolMetricsRollingStatisticalWindowBuckets(Integer threadPoolMetricsRollingStatisticalWindowBuckets) {
        this.threadPoolMetricsRollingStatisticalWindowBuckets = threadPoolMetricsRollingStatisticalWindowBuckets;
    }

    public Boolean getCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    /**
     * Whether to use a {@link HystrixCircuitBreaker} or not. If false no circuit-breaker logic will be used and all requests permitted.
     * <p>
     * This is similar in effect to {@link #setCircuitBreakerForceClosed(Boolean)} except that continues tracking metrics and knowing whether it
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
     * It will stay tripped for the duration defined in {@link #getCircuitBreakerSleepWindowInMilliseconds()};
     * <p>
     * The error percentage this is compared against comes from {@link HystrixCommandMetrics#getHealthCounts()}.
     */
    public void setCircuitBreakerErrorThresholdPercentage(Integer circuitBreakerErrorThresholdPercentage) {
        this.circuitBreakerErrorThresholdPercentage = circuitBreakerErrorThresholdPercentage;
    }

    public Boolean getCircuitBreakerForceClosed() {
        return circuitBreakerForceClosed;
    }

    /**
     * If true the {@link HystrixCircuitBreaker#allowRequest()} will always return true to allow requests regardless of the error percentage from {@link HystrixCommandMetrics#getHealthCounts()}.
     * <p>
     * The circuitBreakerForceOpen property takes precedence so if it set to true this property does nothing.
     */
    public void setCircuitBreakerForceClosed(Boolean circuitBreakerForceClosed) {
        this.circuitBreakerForceClosed = circuitBreakerForceClosed;
    }

    public Boolean getCircuitBreakerForceOpen() {
        return circuitBreakerForceOpen;
    }

    /**
     * If true the {@link HystrixCircuitBreaker#allowRequest()} will always return false, causing the circuit to be open (tripped) and reject all requests.
     * <p>
     * This property takes precedence over circuitBreakerForceClosed
     */
    public void setCircuitBreakerForceOpen(Boolean circuitBreakerForceOpen) {
        this.circuitBreakerForceOpen = circuitBreakerForceOpen;
    }

    public Integer getCircuitBreakerRequestVolumeThreshold() {
        return circuitBreakerRequestVolumeThreshold;
    }

    /**
     * Minimum number of requests in the {@link #setMetricsRollingStatisticalWindowInMilliseconds(Integer)} that must exist before the {@link HystrixCircuitBreaker} will trip.
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
     * The time in milliseconds after a {@link HystrixCircuitBreaker} trips open that it should wait before trying requests again.
     */
    public void setCircuitBreakerSleepWindowInMilliseconds(Integer circuitBreakerSleepWindowInMilliseconds) {
        this.circuitBreakerSleepWindowInMilliseconds = circuitBreakerSleepWindowInMilliseconds;
    }

    public Integer getExecutionIsolationSemaphoreMaxConcurrentRequests() {
        return executionIsolationSemaphoreMaxConcurrentRequests;
    }

    /**
     * Number of concurrent requests permitted to {@link HystrixCommand#run()}. Requests beyond the concurrent limit will be rejected.
     * <p>
     * Applicable only when {@link #getExecutionIsolationStrategy()} == SEMAPHORE.
     */
    public void setExecutionIsolationSemaphoreMaxConcurrentRequests(Integer executionIsolationSemaphoreMaxConcurrentRequests) {
        this.executionIsolationSemaphoreMaxConcurrentRequests = executionIsolationSemaphoreMaxConcurrentRequests;
    }

    public String getExecutionIsolationStrategy() {
        return executionIsolationStrategy;
    }

    /**
     * What isolation strategy {@link HystrixCommand#run()} will be executed with.
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
     * Applicable only when executionIsolationStrategy == THREAD.
     */
    public void setExecutionIsolationThreadInterruptOnTimeout(Boolean executionIsolationThreadInterruptOnTimeout) {
        this.executionIsolationThreadInterruptOnTimeout = executionIsolationThreadInterruptOnTimeout;
    }

    public Integer getExecutionTimeoutInMilliseconds() {
        return executionTimeoutInMilliseconds;
    }

    /**
     * Allow a dynamic override of the {@link HystrixThreadPoolKey} that will dynamically change which {@link HystrixThreadPool} a {@link HystrixCommand} executes on.
     * <p>
     * Typically this should return NULL which will cause it to use the {@link HystrixThreadPoolKey} injected into a {@link HystrixCommand} or derived from the {@link HystrixCommandGroupKey}.
     * <p>
     * When set the injected or derived values will be ignored and a new {@link HystrixThreadPool} created (if necessary) and the {@link HystrixCommand} will begin using the newly defined pool.
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
     * Number of concurrent requests permitted to {@link HystrixCommand#getFallback()}. Requests beyond the concurrent limit will fail-fast and not attempt retrieving a fallback.
     */
    public void setFallbackIsolationSemaphoreMaxConcurrentRequests(Integer fallbackIsolationSemaphoreMaxConcurrentRequests) {
        this.fallbackIsolationSemaphoreMaxConcurrentRequests = fallbackIsolationSemaphoreMaxConcurrentRequests;
    }

    public Boolean getFallbackEnabled() {
        return fallbackEnabled;
    }

    /**
     * Whether {@link HystrixCommand#getFallback()} should be attempted when failure occurs.
     */
    public void setFallbackEnabled(Boolean fallbackEnabled) {
        this.fallbackEnabled = fallbackEnabled;
    }

    public Integer getMetricsHealthSnapshotIntervalInMilliseconds() {
        return metricsHealthSnapshotIntervalInMilliseconds;
    }

    /**
     * Time in milliseconds to wait between allowing health snapshots to be taken that calculate success and error percentages and affect {@link HystrixCircuitBreaker#isOpen()} status.
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
     * Maximum number of values stored in each bucket of the rolling percentile. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     */
    public void setMetricsRollingPercentileBucketSize(Integer metricsRollingPercentileBucketSize) {
        this.metricsRollingPercentileBucketSize = metricsRollingPercentileBucketSize;
    }

    public Boolean getMetricsRollingPercentileEnabled() {
        return metricsRollingPercentileEnabled;
    }

    /**
     * Whether percentile metrics should be captured using {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     */
    public void setMetricsRollingPercentileEnabled(Boolean metricsRollingPercentileEnabled) {
        this.metricsRollingPercentileEnabled = metricsRollingPercentileEnabled;
    }

    /**
     * Duration of percentile rolling window in milliseconds. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     */
    public Integer getMetricsRollingPercentileWindowInMilliseconds() {
        return metricsRollingPercentileWindowInMilliseconds;
    }

    public void setMetricsRollingPercentileWindowInMilliseconds(Integer metricsRollingPercentileWindowInMilliseconds) {
        this.metricsRollingPercentileWindowInMilliseconds = metricsRollingPercentileWindowInMilliseconds;
    }

    public Integer getMetricsRollingPercentileWindowBuckets() {
        return metricsRollingPercentileWindowBuckets;
    }

    /**
     * Number of buckets the rolling percentile window is broken into. This is passed into {@link HystrixRollingPercentile} inside {@link HystrixCommandMetrics}.
     */
    public void setMetricsRollingPercentileWindowBuckets(Integer metricsRollingPercentileWindowBuckets) {
        this.metricsRollingPercentileWindowBuckets = metricsRollingPercentileWindowBuckets;
    }

    public Integer getMetricsRollingStatisticalWindowInMilliseconds() {
        return metricsRollingStatisticalWindowInMilliseconds;
    }

    /**
     * Duration of statistical rolling window in milliseconds. This is passed into {@link HystrixRollingNumber} inside {@link HystrixCommandMetrics}.
     */
    public void setMetricsRollingStatisticalWindowInMilliseconds(Integer metricsRollingStatisticalWindowInMilliseconds) {
        this.metricsRollingStatisticalWindowInMilliseconds = metricsRollingStatisticalWindowInMilliseconds;
    }

    public Integer getMetricsRollingStatisticalWindowBuckets() {
        return metricsRollingStatisticalWindowBuckets;
    }

    /**
     * Number of buckets the rolling statistical window is broken into. This is passed into {@link HystrixRollingNumber} inside {@link HystrixCommandMetrics}.
     */
    public void setMetricsRollingStatisticalWindowBuckets(Integer metricsRollingStatisticalWindowBuckets) {
        this.metricsRollingStatisticalWindowBuckets = metricsRollingStatisticalWindowBuckets;
    }

    public Boolean getRequestCacheEnabled() {
        return requestCacheEnabled;
    }

    /**
     * Whether {@link HystrixCommand#getCacheKey()} should be used with {@link HystrixRequestCache} to provide de-duplication functionality via request-scoped caching.
     */
    public void setRequestCacheEnabled(Boolean requestCacheEnabled) {
        this.requestCacheEnabled = requestCacheEnabled;
    }

    public Boolean getRequestLogEnabled() {
        return requestLogEnabled;
    }

    /**
     * Whether {@link HystrixCommand} execution and events should be logged to {@link HystrixRequestLog}.
     */
    public void setRequestLogEnabled(Boolean requestLogEnabled) {
        this.requestLogEnabled = requestLogEnabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * Whether to include a number of headers with metrics details of the circuit breaker utilization
     */
    public void setMetricsEnabled(boolean metricsEnabled) {
        this.metricsEnabled = metricsEnabled;
    }
}
