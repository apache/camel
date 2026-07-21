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
package org.apache.camel.main;

import java.util.concurrent.ForkJoinPool;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Global configuration for Resilience EIP circuit breaker.
 */
@Configurer(extended = true)
public class Resilience4jConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    private String circuitBreaker;
    private String config;
    @Metadata(defaultValue = "50")
    private Float failureRateThreshold;
    @Metadata(defaultValue = "10")
    private Integer permittedNumberOfCallsInHalfOpenState;
    @Metadata(defaultValue = "false")
    private Boolean throwExceptionWhenHalfOpenOrOpenState;
    @Metadata(defaultValue = "100")
    private Integer slidingWindowSize;
    @Metadata(defaultValue = "COUNT_BASED", enums = "COUNT_BASED,TIME_BASED")
    private String slidingWindowType;
    @Metadata(defaultValue = "100")
    private Integer minimumNumberOfCalls;
    private Boolean writableStackTraceEnabled;
    @Metadata(defaultValue = "60s")
    private String waitDurationInOpenState;
    private Boolean automaticTransitionFromOpenToHalfOpenEnabled;
    @Metadata(defaultValue = "100")
    private Float slowCallRateThreshold;
    @Metadata(defaultValue = "60s")
    private String slowCallDurationThreshold;
    @Metadata(defaultValue = "false")
    private Boolean bulkheadEnabled;
    private Integer bulkheadMaxConcurrentCalls;
    private String bulkheadMaxWaitDuration;
    @Metadata(defaultValue = "false")
    private Boolean timeoutEnabled;
    private String timeoutExecutorService;
    @Metadata(defaultValue = "1000")
    private String timeoutDuration;
    @Metadata(defaultValue = "true")
    private Boolean timeoutCancelRunningFuture;
    @Metadata(defaultValue = "false")
    private Boolean micrometerEnabled;
    @Metadata(defaultValue = "SYNCHRONIZED", enums = "LOCK_FREE,SYNCHRONIZED")
    private String slidingWindowSynchronizationStrategy;
    @Metadata(defaultValue = "0")
    private String maxWaitDurationInHalfOpenState;
    @Metadata(defaultValue = "true")
    private Boolean bulkheadFairCallHandlingEnabled;

    public Resilience4jConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
    }

    // getter and setters
    // --------------------------------------------------------------

    public String getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreaker instance to lookup and use from the
     * registry. When using this, then any other circuit breaker options are not in use.
     */
    public void setCircuitBreaker(String circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public String getConfig() {
        return config;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreakerConfig instance to lookup and use from
     * the registry.
     */
    public void setConfig(String config) {
        this.config = config;
    }

    public Float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    /**
     * Configures the failure rate threshold in percentage. If the failure rate is equal or greater than the threshold
     * the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public void setFailureRateThreshold(Float failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public Integer getPermittedNumberOfCallsInHalfOpenState() {
        return permittedNumberOfCallsInHalfOpenState;
    }

    /**
     * Configures the number of permitted calls when the CircuitBreaker is half open.
     * <p>
     * The size must be greater than 0. Default size is 10.
     */
    public void setPermittedNumberOfCallsInHalfOpenState(Integer permittedNumberOfCallsInHalfOpenState) {
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
    }

    public Boolean getThrowExceptionWhenHalfOpenOrOpenState() {
        return throwExceptionWhenHalfOpenOrOpenState;
    }

    /**
     * Whether to throw io.github.resilience4j.circuitbreaker.CallNotPermittedException when the call is rejected due
     * circuit breaker is half open or open.
     */
    public void setThrowExceptionWhenHalfOpenOrOpenState(Boolean throwExceptionWhenHalfOpenOrOpenState) {
        this.throwExceptionWhenHalfOpenOrOpenState = throwExceptionWhenHalfOpenOrOpenState;
    }

    public Integer getSlidingWindowSize() {
        return slidingWindowSize;
    }

    /**
     * Configures the size of the sliding window which is used to record the outcome of calls when the CircuitBreaker is
     * closed. {@code slidingWindowSize} configures the size of the sliding window. Sliding window can either be
     * count-based or time-based.
     *
     * If {@code slidingWindowType} is COUNT_BASED, the last {@code slidingWindowSize} calls are recorded and
     * aggregated. If {@code slidingWindowType} is TIME_BASED, the calls of the last {@code slidingWindowSize} seconds
     * are recorded and aggregated.
     * <p>
     * The {@code slidingWindowSize} must be greater than 0. The {@code minimumNumberOfCalls} must be greater than 0. If
     * the slidingWindowType is COUNT_BASED, the {@code minimumNumberOfCalls} cannot be greater than
     * {@code slidingWindowSize}. If the slidingWindowType is TIME_BASED, you can pick whatever you want.
     *
     * Default slidingWindowSize is 100.
     */
    public void setSlidingWindowSize(Integer slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
    }

    public String getSlidingWindowType() {
        return slidingWindowType;
    }

    /**
     * Configures the type of the sliding window which is used to record the outcome of calls when the CircuitBreaker is
     * closed. Sliding window can either be count-based or time-based.
     *
     * If {@code slidingWindowType} is COUNT_BASED, the last {@code slidingWindowSize} calls are recorded and
     * aggregated. If {@code slidingWindowType} is TIME_BASED, the calls of the last {@code slidingWindowSize} seconds
     * are recorded and aggregated.
     *
     * Default slidingWindowType is COUNT_BASED.
     */
    public void setSlidingWindowType(String slidingWindowType) {
        this.slidingWindowType = slidingWindowType;
    }

    public Integer getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    /**
     * Configures configures the minimum number of calls which are required (per sliding window period) before the
     * CircuitBreaker can calculate the error rate. For example, if {@code minimumNumberOfCalls} is 10, then at least 10
     * calls must be recorded, before the failure rate can be calculated. If only 9 calls have been recorded the
     * CircuitBreaker will not transition to open even if all 9 calls have failed.
     *
     * Default minimumNumberOfCalls is 100
     */
    public void setMinimumNumberOfCalls(Integer minimumNumberOfCalls) {
        this.minimumNumberOfCalls = minimumNumberOfCalls;
    }

    public Boolean getWritableStackTraceEnabled() {
        return writableStackTraceEnabled;
    }

    /**
     * Enables writable stack traces. When set to false, Exception.getStackTrace returns a zero length array. This may
     * be used to reduce log spam when the circuit breaker is open as the cause of the exceptions is already known (the
     * circuit breaker is short-circuiting calls).
     */
    public void setWritableStackTraceEnabled(Boolean writableStackTraceEnabled) {
        this.writableStackTraceEnabled = writableStackTraceEnabled;
    }

    public String getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    /**
     * Configures the wait duration which specifies how long the CircuitBreaker should stay open, before it switches to
     * half open. Accepts Camel duration expressions (e.g. 60s, 1m) or millisecond values. Default value is 60 seconds.
     */
    public void setWaitDurationInOpenState(String waitDurationInOpenState) {
        this.waitDurationInOpenState = waitDurationInOpenState;
    }

    public Boolean getAutomaticTransitionFromOpenToHalfOpenEnabled() {
        return automaticTransitionFromOpenToHalfOpenEnabled;
    }

    /**
     * Enables automatic transition from OPEN to HALF_OPEN state once the waitDurationInOpenState has passed.
     */
    public void setAutomaticTransitionFromOpenToHalfOpenEnabled(Boolean automaticTransitionFromOpenToHalfOpenEnabled) {
        this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
    }

    public Float getSlowCallRateThreshold() {
        return slowCallRateThreshold;
    }

    /**
     * Configures a threshold in percentage. The CircuitBreaker considers a call as slow when the call duration is
     * greater than slowCallDurationThreshold(Duration. When the percentage of slow calls is equal or greater the
     * threshold, the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 100 percentage which means that
     * all recorded calls must be slower than slowCallDurationThreshold.
     */
    public void setSlowCallRateThreshold(Float slowCallRateThreshold) {
        this.slowCallRateThreshold = slowCallRateThreshold;
    }

    public String getSlowCallDurationThreshold() {
        return slowCallDurationThreshold;
    }

    /**
     * Configures the duration threshold above which calls are considered as slow and increase the slow calls
     * percentage. Accepts Camel duration expressions (e.g. 60s, 1m) or millisecond values. Default value is 60 seconds.
     */
    public void setSlowCallDurationThreshold(String slowCallDurationThreshold) {
        this.slowCallDurationThreshold = slowCallDurationThreshold;
    }

    public Boolean getBulkheadEnabled() {
        return bulkheadEnabled;
    }

    /**
     * Whether bulkhead is enabled or not on the circuit breaker.
     */
    public void setBulkheadEnabled(Boolean bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
    }

    public Integer getBulkheadMaxConcurrentCalls() {
        return bulkheadMaxConcurrentCalls;
    }

    /**
     * Configures the max amount of concurrent calls the bulkhead will support.
     */
    public void setBulkheadMaxConcurrentCalls(Integer bulkheadMaxConcurrentCalls) {
        this.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls;
    }

    public String getBulkheadMaxWaitDuration() {
        return bulkheadMaxWaitDuration;
    }

    /**
     * Configures a maximum amount of time which the calling thread will wait to enter the bulkhead. Accepts Camel
     * duration expressions (e.g. 500ms, 1s) or millisecond values. Default is 0 (no waiting).
     */
    public void setBulkheadMaxWaitDuration(String bulkheadMaxWaitDuration) {
        this.bulkheadMaxWaitDuration = bulkheadMaxWaitDuration;
    }

    public Boolean getTimeoutEnabled() {
        return timeoutEnabled;
    }

    /**
     * Whether timeout is enabled or not on the circuit breaker. Default is false.
     */
    public void setTimeoutEnabled(Boolean timeoutEnabled) {
        this.timeoutEnabled = timeoutEnabled;
    }

    public String getTimeoutExecutorService() {
        return timeoutExecutorService;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled (uses {@link ForkJoinPool#commonPool()} by
     * default)
     */
    public void setTimeoutExecutorService(String timeoutExecutorService) {
        this.timeoutExecutorService = timeoutExecutorService;
    }

    public String getTimeoutDuration() {
        return timeoutDuration;
    }

    /**
     * Configures the thread execution timeout. Accepts Camel duration expressions (e.g. 1s, 500ms) or millisecond
     * values. Default value is 1000 millis (1 second).
     */
    public void setTimeoutDuration(String timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public Boolean getTimeoutCancelRunningFuture() {
        return timeoutCancelRunningFuture;
    }

    /**
     * Configures whether cancel is called on the running future. Defaults to true.
     */
    public void setTimeoutCancelRunningFuture(Boolean timeoutCancelRunningFuture) {
        this.timeoutCancelRunningFuture = timeoutCancelRunningFuture;
    }

    public Boolean getMicrometerEnabled() {
        return micrometerEnabled;
    }

    /**
     * Whether to enable collecting statistics using Micrometer. This requires adding camel-resilience4j-micrometer JAR
     * to the classpath.
     */
    public void setMicrometerEnabled(Boolean micrometerEnabled) {
        this.micrometerEnabled = micrometerEnabled;
    }

    public String getSlidingWindowSynchronizationStrategy() {
        return slidingWindowSynchronizationStrategy;
    }

    /**
     * Configures the synchronization strategy for the sliding window. LOCK_FREE uses a CAS-based lock-free algorithm
     * for better performance under high concurrency. SYNCHRONIZED uses blocking locks with lower memory allocation.
     */
    public void setSlidingWindowSynchronizationStrategy(String slidingWindowSynchronizationStrategy) {
        this.slidingWindowSynchronizationStrategy = slidingWindowSynchronizationStrategy;
    }

    public String getMaxWaitDurationInHalfOpenState() {
        return maxWaitDurationInHalfOpenState;
    }

    /**
     * Configures the maximum wait duration which controls how long the CircuitBreaker should stay in Half Open state,
     * before it switches to open. Value 0 means circuit breaker will wait in half open state until all permitted calls
     * have been completed. Accepts Camel duration expressions (e.g. 10s, 1m) or millisecond values.
     */
    public void setMaxWaitDurationInHalfOpenState(String maxWaitDurationInHalfOpenState) {
        this.maxWaitDurationInHalfOpenState = maxWaitDurationInHalfOpenState;
    }

    public Boolean getBulkheadFairCallHandlingEnabled() {
        return bulkheadFairCallHandlingEnabled;
    }

    /**
     * Configures whether the bulkhead uses a fair calling strategy. When enabled (default), a fair strategy guarantees
     * the order of incoming requests (FIFO). When disabled, no ordering is guaranteed and may improve throughput.
     */
    public void setBulkheadFairCallHandlingEnabled(Boolean bulkheadFairCallHandlingEnabled) {
        this.bulkheadFairCallHandlingEnabled = bulkheadFairCallHandlingEnabled;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreaker instance to lookup and use from the
     * registry. When using this, then any other circuit breaker options are not in use.
     */
    public Resilience4jConfigurationProperties withCircuitBreakerRef(String circuitBreakerRef) {
        this.circuitBreaker = circuitBreakerRef;
        return this;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreakerConfig instance to lookup and use from
     * the registry.
     */
    public Resilience4jConfigurationProperties withConfigRef(String configRef) {
        this.config = configRef;
        return this;
    }

    /**
     * Configures the failure rate threshold in percentage. If the failure rate is equal or greater than the threshold
     * the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public Resilience4jConfigurationProperties withFailureRateThreshold(Float failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
        return this;
    }

    /**
     * Configures the number of permitted calls when the CircuitBreaker is half open.
     * <p>
     * The size must be greater than 0. Default size is 10.
     */
    public Resilience4jConfigurationProperties withPermittedNumberOfCallsInHalfOpenState(
            Integer permittedNumberOfCallsInHalfOpenState) {
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        return this;
    }

    /**
     * Whether to throw io.github.resilience4j.circuitbreaker.CallNotPermittedException when the call is rejected due
     * circuit breaker is half open or open.
     */
    public Resilience4jConfigurationProperties withThrowExceptionWhenHalfOpenOrOpenState(
            Boolean throwExceptionWhenHalfOpenOrOpenState) {
        this.throwExceptionWhenHalfOpenOrOpenState = throwExceptionWhenHalfOpenOrOpenState;
        return this;
    }

    /**
     * Configures the size of the sliding window which is used to record the outcome of calls when the CircuitBreaker is
     * closed. {@code slidingWindowSize} configures the size of the sliding window. Sliding window can either be
     * count-based or time-based.
     *
     * If {@code slidingWindowType} is COUNT_BASED, the last {@code slidingWindowSize} calls are recorded and
     * aggregated. If {@code slidingWindowType} is TIME_BASED, the calls of the last {@code slidingWindowSize} seconds
     * are recorded and aggregated.
     * <p>
     * The {@code slidingWindowSize} must be greater than 0. The {@code minimumNumberOfCalls} must be greater than 0. If
     * the slidingWindowType is COUNT_BASED, the {@code minimumNumberOfCalls} cannot be greater than
     * {@code slidingWindowSize}. If the slidingWindowType is TIME_BASED, you can pick whatever you want.
     *
     * Default slidingWindowSize is 100.
     */
    public Resilience4jConfigurationProperties withSlidingWindowSize(Integer slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
        return this;
    }

    /**
     * Configures the type of the sliding window which is used to record the outcome of calls when the CircuitBreaker is
     * closed. Sliding window can either be count-based or time-based.
     *
     * If {@code slidingWindowType} is COUNT_BASED, the last {@code slidingWindowSize} calls are recorded and
     * aggregated. If {@code slidingWindowType} is TIME_BASED, the calls of the last {@code slidingWindowSize} seconds
     * are recorded and aggregated.
     *
     * Default slidingWindowType is COUNT_BASED.
     */
    public Resilience4jConfigurationProperties withSlidingWindowType(String slidingWindowType) {
        this.slidingWindowType = slidingWindowType;
        return this;
    }

    /**
     * Configures configures the minimum number of calls which are required (per sliding window period) before the
     * CircuitBreaker can calculate the error rate. For example, if {@code minimumNumberOfCalls} is 10, then at least 10
     * calls must be recorded, before the failure rate can be calculated. If only 9 calls have been recorded the
     * CircuitBreaker will not transition to open even if all 9 calls have failed.
     *
     * Default minimumNumberOfCalls is 100
     */
    public Resilience4jConfigurationProperties withMinimumNumberOfCalls(Integer minimumNumberOfCalls) {
        this.minimumNumberOfCalls = minimumNumberOfCalls;
        return this;
    }

    /**
     * Enables writable stack traces. When set to false, Exception.getStackTrace returns a zero length array. This may
     * be used to reduce log spam when the circuit breaker is open as the cause of the exceptions is already known (the
     * circuit breaker is short-circuiting calls).
     */
    public Resilience4jConfigurationProperties withWritableStackTraceEnabled(Boolean writableStackTraceEnabled) {
        this.writableStackTraceEnabled = writableStackTraceEnabled;
        return this;
    }

    /**
     * Configures the wait duration which specifies how long the CircuitBreaker should stay open, before it switches to
     * half open. Accepts Camel duration expressions (e.g. 60s, 1m) or millisecond values. Default value is 60 seconds.
     */
    public Resilience4jConfigurationProperties withWaitDurationInOpenState(String waitDurationInOpenState) {
        this.waitDurationInOpenState = waitDurationInOpenState;
        return this;
    }

    public Resilience4jConfigurationProperties withAutomaticTransitionFromOpenToHalfOpenEnabled(
            Boolean automaticTransitionFromOpenToHalfOpenEnabled) {
        this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
        return this;
    }

    /**
     * Configures a threshold in percentage. The CircuitBreaker considers a call as slow when the call duration is
     * greater than slowCallDurationThreshold(Duration. When the percentage of slow calls is equal or greater the
     * threshold, the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 100 percentage which means that
     * all recorded calls must be slower than slowCallDurationThreshold.
     */
    public Resilience4jConfigurationProperties withSlowCallRateThreshold(Float slowCallRateThreshold) {
        this.slowCallRateThreshold = slowCallRateThreshold;
        return this;
    }

    /**
     * Configures the duration threshold above which calls are considered as slow and increase the slow calls
     * percentage. Accepts Camel duration expressions (e.g. 60s, 1m) or millisecond values. Default value is 60 seconds.
     */
    public Resilience4jConfigurationProperties withSlowCallDurationThreshold(String slowCallDurationThreshold) {
        this.slowCallDurationThreshold = slowCallDurationThreshold;
        return this;
    }

    /**
     * Whether bulkhead is enabled or not on the circuit breaker.
     */
    public Resilience4jConfigurationProperties withBulkheadEnabled(Boolean bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
        return this;
    }

    /**
     * Configures the max amount of concurrent calls the bulkhead will support.
     */
    public Resilience4jConfigurationProperties withBulkheadMaxConcurrentCalls(Integer bulkheadMaxConcurrentCalls) {
        this.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls;
        return this;
    }

    /**
     * Configures a maximum amount of time which the calling thread will wait to enter the bulkhead. Accepts Camel
     * duration expressions (e.g. 500ms, 1s) or millisecond values. Default is 0 (no waiting).
     */
    public Resilience4jConfigurationProperties withBulkheadMaxWaitDuration(String bulkheadMaxWaitDuration) {
        this.bulkheadMaxWaitDuration = bulkheadMaxWaitDuration;
        return this;
    }

    /**
     * Whether timeout is enabled or not on the circuit breaker. Default is false.
     */
    public Resilience4jConfigurationProperties withTimeoutEnabled(Boolean timeoutEnabled) {
        this.timeoutEnabled = timeoutEnabled;
        return this;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled (uses {@link ForkJoinPool#commonPool()} by
     * default)
     */
    public Resilience4jConfigurationProperties withTimeoutExecutorServiceRef(String timeoutExecutorServiceRef) {
        this.timeoutExecutorService = timeoutExecutorServiceRef;
        return this;
    }

    /**
     * Configures the thread execution timeout. Accepts Camel duration expressions (e.g. 1s, 500ms) or millisecond
     * values. Default value is 1000 millis (1 second).
     */
    public Resilience4jConfigurationProperties withTimeoutDuration(String timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
        return this;
    }

    /**
     * Configures whether cancel is called on the running future. Defaults to true.
     */
    public Resilience4jConfigurationProperties withTimeoutCancelRunningFuture(Boolean timeoutCancelRunningFuture) {
        this.timeoutCancelRunningFuture = timeoutCancelRunningFuture;
        return this;
    }

    /**
     * Whether to enable collecting statistics using Micrometer. This requires adding camel-resilience4j-micrometer JAR
     * to the classpath.
     */
    public Resilience4jConfigurationProperties withMicrometerEnabled(Boolean micrometerEnabled) {
        this.micrometerEnabled = micrometerEnabled;
        return this;
    }

    /**
     * Configures the synchronization strategy for the sliding window. LOCK_FREE uses a CAS-based lock-free algorithm
     * for better performance under high concurrency. SYNCHRONIZED uses blocking locks with lower memory allocation.
     */
    public Resilience4jConfigurationProperties withSlidingWindowSynchronizationStrategy(
            String slidingWindowSynchronizationStrategy) {
        this.slidingWindowSynchronizationStrategy = slidingWindowSynchronizationStrategy;
        return this;
    }

    /**
     * Configures the maximum wait duration which controls how long the CircuitBreaker should stay in Half Open state,
     * before it switches to open. Value 0 means circuit breaker will wait in half open state until all permitted calls
     * have been completed. Accepts Camel duration expressions (e.g. 10s, 1m) or millisecond values.
     */
    public Resilience4jConfigurationProperties withMaxWaitDurationInHalfOpenState(
            String maxWaitDurationInHalfOpenState) {
        this.maxWaitDurationInHalfOpenState = maxWaitDurationInHalfOpenState;
        return this;
    }

    /**
     * Configures whether the bulkhead uses a fair calling strategy. When enabled (default), a fair strategy guarantees
     * the order of incoming requests (FIFO). When disabled, no ordering is guaranteed and may improve throughput.
     */
    public Resilience4jConfigurationProperties withBulkheadFairCallHandlingEnabled(
            Boolean bulkheadFairCallHandlingEnabled) {
        this.bulkheadFairCallHandlingEnabled = bulkheadFairCallHandlingEnabled;
        return this;
    }

}
