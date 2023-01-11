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

import java.util.concurrent.ForkJoinPool;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Resilience4j Circuit Breaker EIP configuration
 */
@Metadata(label = "configuration,eip")
@XmlRootElement(name = "resilience4jConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer(extended = true)
public class Resilience4jConfigurationDefinition extends Resilience4jConfigurationCommon {

    @XmlTransient
    private CircuitBreakerDefinition parent;

    public Resilience4jConfigurationDefinition() {
    }

    public Resilience4jConfigurationDefinition(CircuitBreakerDefinition parent) {
        this.parent = parent;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreaker instance to lookup and use from the
     * registry. When using this, then any other circuit breaker options are not in use.
     */
    public Resilience4jConfigurationDefinition circuitBreaker(String circuitBreaker) {
        setCircuitBreaker(circuitBreaker);
        return this;
    }

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreakerConfig instance to lookup and use from
     * the registry.
     */
    public Resilience4jConfigurationDefinition config(String ref) {
        setConfig(ref);
        return this;
    }

    /**
     * Configures the failure rate threshold in percentage. If the failure rate is equal or greater than the threshold
     * the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public Resilience4jConfigurationDefinition failureRateThreshold(float failureRateThreshold) {
        setFailureRateThreshold(Float.toString(failureRateThreshold));
        return this;
    }

    /**
     * Configures the number of permitted calls when the CircuitBreaker is half open.
     * <p>
     * The size must be greater than 0. Default size is 10.
     */
    public Resilience4jConfigurationDefinition permittedNumberOfCallsInHalfOpenState(
            int permittedNumberOfCallsInHalfOpenState) {
        setPermittedNumberOfCallsInHalfOpenState(Integer.toString(permittedNumberOfCallsInHalfOpenState));
        return this;
    }

    /**
     * Whether to throw io.github.resilience4j.circuitbreaker.CallNotPermittedException when the call is rejected due
     * circuit breaker is half open or open.
     */
    public Resilience4jConfigurationDefinition throwExceptionWhenHalfOpenOrOpenState(
            boolean throwExceptionWhenHalfOpenOrOpenState) {
        setThrowExceptionWhenHalfOpenOrOpenState(Boolean.toString(throwExceptionWhenHalfOpenOrOpenState));
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
    public Resilience4jConfigurationDefinition slidingWindowSize(int slidingWindowSize) {
        setSlidingWindowSize(Integer.toString(slidingWindowSize));
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
    public Resilience4jConfigurationDefinition slidingWindowType(String slidingWindowType) {
        setSlidingWindowType(slidingWindowType);
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
    public Resilience4jConfigurationDefinition minimumNumberOfCalls(int minimumNumberOfCalls) {
        setMinimumNumberOfCalls(Integer.toString(minimumNumberOfCalls));
        return this;
    }

    /**
     * Enables writable stack traces. When set to false, Exception.getStackTrace returns a zero length array. This may
     * be used to reduce log spam when the circuit breaker is open as the cause of the exceptions is already known (the
     * circuit breaker is short-circuiting calls).
     */
    public Resilience4jConfigurationDefinition writableStackTraceEnabled(boolean writableStackTraceEnabled) {
        setWritableStackTraceEnabled(Boolean.toString(writableStackTraceEnabled));
        return this;
    }

    /**
     * Configures the wait duration (in seconds) which specifies how long the CircuitBreaker should stay open, before it
     * switches to half open. Default value is 60 seconds.
     */
    public Resilience4jConfigurationDefinition waitDurationInOpenState(int waitDurationInOpenState) {
        setWaitDurationInOpenState(Integer.toString(waitDurationInOpenState));
        return this;
    }

    /**
     * Enables automatic transition from OPEN to HALF_OPEN state once the waitDurationInOpenState has passed.
     */
    public Resilience4jConfigurationDefinition automaticTransitionFromOpenToHalfOpenEnabled(
            boolean automaticTransitionFromOpenToHalfOpenEnabled) {
        setAutomaticTransitionFromOpenToHalfOpenEnabled(Boolean.toString(automaticTransitionFromOpenToHalfOpenEnabled));
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
    public Resilience4jConfigurationDefinition slowCallRateThreshold(float slowCallRateThreshold) {
        setSlowCallRateThreshold(Float.toString(slowCallRateThreshold));
        return this;
    }

    /**
     * Configures the duration threshold (seconds) above which calls are considered as slow and increase the slow calls
     * percentage. Default value is 60 seconds.
     */
    public Resilience4jConfigurationDefinition slowCallDurationThreshold(int slowCallDurationThreshold) {
        setSlowCallDurationThreshold(Integer.toString(slowCallDurationThreshold));
        return this;
    }

    /**
     * Whether bulkhead is enabled or not on the circuit breaker. Default is false.
     */
    public Resilience4jConfigurationDefinition bulkheadEnabled(boolean bulkheadEnabled) {
        setBulkheadEnabled(Boolean.toString(bulkheadEnabled));
        return this;
    }

    /**
     * Configures the max amount of concurrent calls the bulkhead will support.
     */
    public Resilience4jConfigurationDefinition bulkheadMaxConcurrentCalls(int bulkheadMaxConcurrentCalls) {
        setBulkheadMaxConcurrentCalls(Integer.toString(bulkheadMaxConcurrentCalls));
        return this;
    }

    /**
     * Configures a maximum amount of time which the calling thread will wait to enter the bulkhead. If bulkhead has
     * space available, entry is guaranteed and immediate. If bulkhead is full, calling threads will contest for space,
     * if it becomes available. maxWaitDuration can be set to 0.
     * <p>
     * Note: for threads running on an event-loop or equivalent (rx computation pool, etc), setting maxWaitDuration to 0
     * is highly recommended. Blocking an event-loop thread will most likely have a negative effect on application
     * throughput.
     */
    public Resilience4jConfigurationDefinition bulkheadMaxWaitDuration(int bulkheadMaxWaitDuration) {
        setBulkheadMaxWaitDuration(Integer.toString(bulkheadMaxWaitDuration));
        return this;
    }

    /**
     * Whether timeout is enabled or not on the circuit breaker. Default is false.
     */
    public Resilience4jConfigurationDefinition timeoutEnabled(boolean timeoutEnabled) {
        setTimeoutEnabled(Boolean.toString(timeoutEnabled));
        return this;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled (uses {@link ForkJoinPool#commonPool()} by
     * default)
     */
    public Resilience4jConfigurationDefinition timeoutExecutorService(String executorService) {
        setTimeoutExecutorService(executorService);
        return this;
    }

    /**
     * Configures the thread execution timeout (millis). Default value is 1000 millis (1 second).
     */
    public Resilience4jConfigurationDefinition timeoutDuration(int timeoutDuration) {
        setTimeoutDuration(Integer.toString(timeoutDuration));
        return this;
    }

    /**
     * Configures whether cancel is called on the running future. Defaults to true.
     */
    public Resilience4jConfigurationDefinition timeoutCancelRunningFuture(boolean timeoutCancelRunningFuture) {
        setTimeoutCancelRunningFuture(Boolean.toString(timeoutCancelRunningFuture));
        return this;
    }

    /**
     * End of configuration.
     */
    public CircuitBreakerDefinition end() {
        return parent;
    }

}
