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

/**
 * Global configuration for Resilience EIP circuit breaker.
 */
public class Resilience4jConfigurationProperties {

    private final MainConfigurationProperties parent;

    private String circuitBreakerRef;
    private String configRef;
    private Float failureRateThreshold;
    private Integer permittedNumberOfCallsInHalfOpenState;
    private Integer slidingWindowSize;
    private String slidingWindowType;
    private Integer minimumNumberOfCalls;
    private Boolean writableStackTraceEnabled;
    private Integer waitDurationInOpenState;
    private Boolean automaticTransitionFromOpenToHalfOpenEnabled;
    private Float slowCallRateThreshold;
    private Integer slowCallDurationThreshold;
    private Boolean bulkheadEnabled;
    private Integer bulkheadMaxConcurrentCalls;
    private Integer bulkheadMaxWaitDuration;
    private Boolean timeoutEnabled;
    private String timeoutExecutorServiceRef;
    private Integer timeoutDuration;
    private Boolean timeoutCancelRunningFuture;

    public Resilience4jConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    // getter and setters
    // --------------------------------------------------------------

    public String getCircuitBreakerRef() {
        return circuitBreakerRef;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreaker instance
     * to lookup and use from the registry. When using this, then any other circuit breaker options
     * are not in use.
     */
    public void setCircuitBreakerRef(String circuitBreakerRef) {
        this.circuitBreakerRef = circuitBreakerRef;
    }

    public String getConfigRef() {
        return configRef;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreakerConfig instance
     * to lookup and use from the registry.
     */
    public void setConfigRef(String configRef) {
        this.configRef = configRef;
    }

    public Float getFailureRateThreshold() {
        return failureRateThreshold;
    }

    /**
     * Configures the failure rate threshold in percentage.
     * If the failure rate is equal or greater than the threshold the CircuitBreaker transitions to open and starts short-circuiting calls.
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

    public Integer getSlidingWindowSize() {
        return slidingWindowSize;
    }

    /**
     * Configures the size of the sliding window which is used to record the outcome of calls when the CircuitBreaker is closed.
     * {@code slidingWindowSize} configures the size of the sliding window. Sliding window can either be count-based or time-based.
     *
     * If {@code slidingWindowType} is COUNT_BASED, the last {@code slidingWindowSize} calls are recorded and aggregated.
     * If {@code slidingWindowType} is TIME_BASED, the calls of the last {@code slidingWindowSize} seconds are recorded and aggregated.
     * <p>
     * The {@code slidingWindowSize} must be greater than 0.
     * The {@code minimumNumberOfCalls} must be greater than 0.
     * If the slidingWindowType is COUNT_BASED, the {@code minimumNumberOfCalls} cannot be greater than {@code slidingWindowSize}.
     * If the slidingWindowType is TIME_BASED, you can pick whatever you want.
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
     * Configures the type of the sliding window which is used to record the outcome of calls when the CircuitBreaker is closed.
     * Sliding window can either be count-based or time-based.
     *
     * If {@code slidingWindowType} is COUNT_BASED, the last {@code slidingWindowSize} calls are recorded and aggregated.
     * If {@code slidingWindowType} is TIME_BASED, the calls of the last {@code slidingWindowSize} seconds are recorded and aggregated.
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
     * Configures configures the minimum number of calls which are required (per sliding window period) before the CircuitBreaker can calculate the error rate.
     * For example, if {@code minimumNumberOfCalls} is 10, then at least 10 calls must be recorded, before the failure rate can be calculated.
     * If only 9 calls have been recorded the CircuitBreaker will not transition to open even if all 9 calls have failed.
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
     * Enables writable stack traces. When set to false, Exception.getStackTrace returns a zero length array.
     * This may be used to reduce log spam when the circuit breaker is open as the cause of the exceptions is already known (the circuit breaker is short-circuiting calls).
     */
    public void setWritableStackTraceEnabled(Boolean writableStackTraceEnabled) {
        this.writableStackTraceEnabled = writableStackTraceEnabled;
    }

    public Integer getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    /**
     * Configures the wait duration (in seconds) which specifies how long the CircuitBreaker should stay open, before it switches to half open.
     * Default value is 60 seconds.
     */
    public void setWaitDurationInOpenState(Integer waitDurationInOpenState) {
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
     * Configures a threshold in percentage. The CircuitBreaker considers a call as slow when the call duration is greater than slowCallDurationThreshold(Duration.
     * When the percentage of slow calls is equal or greater the threshold, the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100.
     * Default value is 100 percentage which means that all recorded calls must be slower than slowCallDurationThreshold.
     */
    public void setSlowCallRateThreshold(Float slowCallRateThreshold) {
        this.slowCallRateThreshold = slowCallRateThreshold;
    }

    public Integer getSlowCallDurationThreshold() {
        return slowCallDurationThreshold;
    }

    /**
     * Configures the duration threshold (seconds) above which calls are considered as slow and increase the slow calls percentage.
     * Default value is 60 seconds.
     */
    public void setSlowCallDurationThreshold(Integer slowCallDurationThreshold) {
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

    public Integer getBulkheadMaxWaitDuration() {
        return bulkheadMaxWaitDuration;
    }

    /**
     * Configures a maximum amount of time which the calling thread will wait to enter the bulkhead. If bulkhead has space available, entry
     * is guaranteed and immediate. If bulkhead is full, calling threads will contest for space, if it becomes available. maxWaitDuration can be set to 0.
     * <p>
     * Note: for threads running on an event-loop or equivalent (rx computation pool, etc), setting maxWaitDuration to 0 is highly recommended. Blocking
     * an event-loop thread will most likely have a negative effect on application throughput.
     */
    public void setBulkheadMaxWaitDuration(Integer bulkheadMaxWaitDuration) {
        this.bulkheadMaxWaitDuration = bulkheadMaxWaitDuration;
    }

    public Boolean getTimeoutEnabled() {
        return timeoutEnabled;
    }

    /**
     * Whether timeout is enabled or not on the circuit breaker.
     * Default is false.
     */
    public void setTimeoutEnabled(Boolean timeoutEnabled) {
        this.timeoutEnabled = timeoutEnabled;
    }

    public String getTimeoutExecutorServiceRef() {
        return timeoutExecutorServiceRef;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled (uses {@link ForkJoinPool#commonPool()} by default)
     */
    public void setTimeoutExecutorServiceRef(String timeoutExecutorServiceRef) {
        this.timeoutExecutorServiceRef = timeoutExecutorServiceRef;
    }

    public Integer getTimeoutDuration() {
        return timeoutDuration;
    }

    /**
     * Configures the thread execution timeout (millis).
     * Default value is 1000 millis (1 second).
     */
    public void setTimeoutDuration(Integer timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public Boolean getTimeoutCancelRunningFuture() {
        return timeoutCancelRunningFuture;
    }

    /**
     * Configures whether cancel is called on the running future.
     * Defaults to true.
     */
    public void setTimeoutCancelRunningFuture(Boolean timeoutCancelRunningFuture) {
        this.timeoutCancelRunningFuture = timeoutCancelRunningFuture;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreaker instance
     * to lookup and use from the registry. When using this, then any other circuit breaker options
     * are not in use.
     */
    public Resilience4jConfigurationProperties withCircuitBreakerRef(String circuitBreakerRef) {
        this.circuitBreakerRef = circuitBreakerRef;
        return this;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreakerConfig instance
     * to lookup and use from the registry.
     */
    public Resilience4jConfigurationProperties withConfigRef(String configRef) {
        this.configRef = configRef;
        return this;
    }

    /**
     * Configures the failure rate threshold in percentage.
     * If the failure rate is equal or greater than the threshold the CircuitBreaker transitions to open and starts short-circuiting calls.
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
    public Resilience4jConfigurationProperties withPermittedNumberOfCallsInHalfOpenState(Integer permittedNumberOfCallsInHalfOpenState) {
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        return this;
    }

    /**
     * Configures the size of the sliding window which is used to record the outcome of calls when the CircuitBreaker is closed.
     * {@code slidingWindowSize} configures the size of the sliding window. Sliding window can either be count-based or time-based.
     *
     * If {@code slidingWindowType} is COUNT_BASED, the last {@code slidingWindowSize} calls are recorded and aggregated.
     * If {@code slidingWindowType} is TIME_BASED, the calls of the last {@code slidingWindowSize} seconds are recorded and aggregated.
     * <p>
     * The {@code slidingWindowSize} must be greater than 0.
     * The {@code minimumNumberOfCalls} must be greater than 0.
     * If the slidingWindowType is COUNT_BASED, the {@code minimumNumberOfCalls} cannot be greater than {@code slidingWindowSize}.
     * If the slidingWindowType is TIME_BASED, you can pick whatever you want.
     *
     * Default slidingWindowSize is 100.
     */
    public Resilience4jConfigurationProperties withSlidingWindowSize(Integer slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
        return this;
    }

    /**
     * Configures the type of the sliding window which is used to record the outcome of calls when the CircuitBreaker is closed.
     * Sliding window can either be count-based or time-based.
     *
     * If {@code slidingWindowType} is COUNT_BASED, the last {@code slidingWindowSize} calls are recorded and aggregated.
     * If {@code slidingWindowType} is TIME_BASED, the calls of the last {@code slidingWindowSize} seconds are recorded and aggregated.
     *
     * Default slidingWindowType is COUNT_BASED.
     */
    public Resilience4jConfigurationProperties withSlidingWindowType(String slidingWindowType) {
        this.slidingWindowType = slidingWindowType;
        return this;
    }

    /**
     * Configures configures the minimum number of calls which are required (per sliding window period) before the CircuitBreaker can calculate the error rate.
     * For example, if {@code minimumNumberOfCalls} is 10, then at least 10 calls must be recorded, before the failure rate can be calculated.
     * If only 9 calls have been recorded the CircuitBreaker will not transition to open even if all 9 calls have failed.
     *
     * Default minimumNumberOfCalls is 100
     */
    public Resilience4jConfigurationProperties withMinimumNumberOfCalls(Integer minimumNumberOfCalls) {
        this.minimumNumberOfCalls = minimumNumberOfCalls;
        return this;
    }

    /**
     * Enables writable stack traces. When set to false, Exception.getStackTrace returns a zero length array.
     * This may be used to reduce log spam when the circuit breaker is open as the cause of the exceptions is already known (the circuit breaker is short-circuiting calls).
     */
    public Resilience4jConfigurationProperties withWritableStackTraceEnabled(Boolean writableStackTraceEnabled) {
        this.writableStackTraceEnabled = writableStackTraceEnabled;
        return this;
    }

    /**
     * Configures the wait duration (in seconds) which specifies how long the CircuitBreaker should stay open, before it switches to half open.
     * Default value is 60 seconds.
     */
    public Resilience4jConfigurationProperties withWaitDurationInOpenState(Integer waitDurationInOpenState) {
        this.waitDurationInOpenState = waitDurationInOpenState;
        return this;
    }

    public Resilience4jConfigurationProperties withAutomaticTransitionFromOpenToHalfOpenEnabled(Boolean automaticTransitionFromOpenToHalfOpenEnabled) {
        this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
        return this;
    }

    /**
     * Configures a threshold in percentage. The CircuitBreaker considers a call as slow when the call duration is greater than slowCallDurationThreshold(Duration.
     * When the percentage of slow calls is equal or greater the threshold, the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100.
     * Default value is 100 percentage which means that all recorded calls must be slower than slowCallDurationThreshold.
     */
    public Resilience4jConfigurationProperties withSlowCallRateThreshold(Float slowCallRateThreshold) {
        this.slowCallRateThreshold = slowCallRateThreshold;
        return this;
    }

    /**
     * Configures the duration threshold (seconds) above which calls are considered as slow and increase the slow calls percentage.
     * Default value is 60 seconds.
     */
    public Resilience4jConfigurationProperties withSlowCallDurationThreshold(Integer slowCallDurationThreshold) {
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
     * Configures a maximum amount of time which the calling thread will wait to enter the bulkhead. If bulkhead has space available, entry
     * is guaranteed and immediate. If bulkhead is full, calling threads will contest for space, if it becomes available. maxWaitDuration can be set to 0.
     * <p>
     * Note: for threads running on an event-loop or equivalent (rx computation pool, etc), setting maxWaitDuration to 0 is highly recommended. Blocking
     * an event-loop thread will most likely have a negative effect on application throughput.
     */
    public Resilience4jConfigurationProperties withBulkheadMaxWaitDuration(Integer bulkheadMaxWaitDuration) {
        this.bulkheadMaxWaitDuration = bulkheadMaxWaitDuration;
        return this;
    }

    /**
     * Whether timeout is enabled or not on the circuit breaker.
     * Default is false.
     */
    public Resilience4jConfigurationProperties withTimeoutEnabled(Boolean timeoutEnabled) {
        this.timeoutEnabled = timeoutEnabled;
        return this;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled (uses {@link ForkJoinPool#commonPool()} by default)
     */
    public Resilience4jConfigurationProperties withTimeoutExecutorServiceRef(String timeoutExecutorServiceRef) {
        this.timeoutExecutorServiceRef = timeoutExecutorServiceRef;
        return this;
    }

    /**
     * Configures the thread execution timeout (millis).
     * Default value is 1000 millis (1 second).
     */
    public Resilience4jConfigurationProperties withTimeoutDuration(Integer timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
        return this;
    }

    /**
     * Configures whether cancel is called on the running future.
     * Defaults to true.
     */
    public Resilience4jConfigurationProperties withTimeoutCancelRunningFuture(Boolean timeoutCancelRunningFuture) {
        this.timeoutCancelRunningFuture = timeoutCancelRunningFuture;
        return this;
    }

}
