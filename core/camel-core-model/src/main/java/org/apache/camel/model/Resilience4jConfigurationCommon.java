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
import jakarta.xml.bind.annotation.XmlAttribute;

import org.apache.camel.spi.Metadata;

@XmlAccessorType(XmlAccessType.FIELD)
public class Resilience4jConfigurationCommon extends IdentifiedType {

    @XmlAttribute
    @Metadata(label = "advanced")
    private String circuitBreaker;
    @XmlAttribute
    @Metadata(label = "advanced")
    private String config;
    @XmlAttribute
    @Metadata(defaultValue = "50", javaType = "java.lang.Float")
    private String failureRateThreshold;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "10", javaType = "java.lang.Integer")
    private String permittedNumberOfCallsInHalfOpenState;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String throwExceptionWhenHalfOpenOrOpenState;
    @XmlAttribute
    @Metadata(defaultValue = "100", javaType = "java.lang.Integer")
    private String slidingWindowSize;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "COUNT_BASED", enums = "TIME_BASED,COUNT_BASED")
    private String slidingWindowType;
    @XmlAttribute
    @Metadata(defaultValue = "100", javaType = "java.lang.Integer")
    private String minimumNumberOfCalls;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String writableStackTraceEnabled;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "60", javaType = "java.lang.Integer")
    private String waitDurationInOpenState;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "false", javaType = "java.lang.Boolean")
    private String automaticTransitionFromOpenToHalfOpenEnabled;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "100", javaType = "java.lang.Float")
    private String slowCallRateThreshold;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "60", javaType = "java.lang.Integer")
    private String slowCallDurationThreshold;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String bulkheadEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "25", javaType = "java.lang.Integer")
    private String bulkheadMaxConcurrentCalls;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "0", javaType = "java.lang.Integer")
    private String bulkheadMaxWaitDuration;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean")
    private String timeoutEnabled;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.util.concurrent.ExecutorService")
    private String timeoutExecutorService;
    @XmlAttribute
    @Metadata(defaultValue = "1000", javaType = "java.lang.Integer")
    private String timeoutDuration;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String timeoutCancelRunningFuture;

    // Getter/Setter
    // -------------------------------------------------------------------------

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

    public String getFailureRateThreshold() {
        return failureRateThreshold;
    }

    /**
     * Configures the failure rate threshold in percentage. If the failure rate is equal or greater than the threshold
     * the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public void setFailureRateThreshold(String failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public String getPermittedNumberOfCallsInHalfOpenState() {
        return permittedNumberOfCallsInHalfOpenState;
    }

    /**
     * Configures the number of permitted calls when the CircuitBreaker is half open.
     * <p>
     * The size must be greater than 0. Default size is 10.
     */
    public void setPermittedNumberOfCallsInHalfOpenState(String permittedNumberOfCallsInHalfOpenState) {
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
    }

    public String getThrowExceptionWhenHalfOpenOrOpenState() {
        return throwExceptionWhenHalfOpenOrOpenState;
    }

    /**
     * Whether to throw io.github.resilience4j.circuitbreaker.CallNotPermittedException when the call is rejected due
     * circuit breaker is half open or open.
     */
    public void setThrowExceptionWhenHalfOpenOrOpenState(String throwExceptionWhenHalfOpenOrOpenState) {
        this.throwExceptionWhenHalfOpenOrOpenState = throwExceptionWhenHalfOpenOrOpenState;
    }

    public String getSlidingWindowSize() {
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
    public void setSlidingWindowSize(String slidingWindowSize) {
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

    public String getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    /**
     * Configures the minimum number of calls which are required (per sliding window period) before the CircuitBreaker
     * can calculate the error rate. For example, if {@code minimumNumberOfCalls} is 10, then at least 10 calls must be
     * recorded, before the failure rate can be calculated. If only 9 calls have been recorded the CircuitBreaker will
     * not transition to open even if all 9 calls have failed.
     *
     * Default minimumNumberOfCalls is 100
     */
    public void setMinimumNumberOfCalls(String minimumNumberOfCalls) {
        this.minimumNumberOfCalls = minimumNumberOfCalls;
    }

    public String getWritableStackTraceEnabled() {
        return writableStackTraceEnabled;
    }

    /**
     * Enables writable stack traces. When set to false, Exception.getStackTrace returns a zero length array. This may
     * be used to reduce log spam when the circuit breaker is open as the cause of the exceptions is already known (the
     * circuit breaker is short-circuiting calls).
     */
    public void setWritableStackTraceEnabled(String writableStackTraceEnabled) {
        this.writableStackTraceEnabled = writableStackTraceEnabled;
    }

    public String getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    /**
     * Configures the wait duration (in seconds) which specifies how long the CircuitBreaker should stay open, before it
     * switches to half open. Default value is 60 seconds.
     */
    public void setWaitDurationInOpenState(String waitDurationInOpenState) {
        this.waitDurationInOpenState = waitDurationInOpenState;
    }

    public String getAutomaticTransitionFromOpenToHalfOpenEnabled() {
        return automaticTransitionFromOpenToHalfOpenEnabled;
    }

    /**
     * Enables automatic transition from OPEN to HALF_OPEN state once the waitDurationInOpenState has passed.
     */
    public void setAutomaticTransitionFromOpenToHalfOpenEnabled(String automaticTransitionFromOpenToHalfOpenEnabled) {
        this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
    }

    public String getSlowCallRateThreshold() {
        return slowCallRateThreshold;
    }

    /**
     * Configures a threshold in percentage. The CircuitBreaker considers a call as slow when the call duration is
     * greater than slowCallDurationThreshold Duration. When the percentage of slow calls is equal or greater the
     * threshold, the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 100 percentage which means that
     * all recorded calls must be slower than slowCallDurationThreshold.
     */
    public void setSlowCallRateThreshold(String slowCallRateThreshold) {
        this.slowCallRateThreshold = slowCallRateThreshold;
    }

    public String getSlowCallDurationThreshold() {
        return slowCallDurationThreshold;
    }

    /**
     * Configures the duration threshold (seconds) above which calls are considered as slow and increase the slow calls
     * percentage. Default value is 60 seconds.
     */
    public void setSlowCallDurationThreshold(String slowCallDurationThreshold) {
        this.slowCallDurationThreshold = slowCallDurationThreshold;
    }

    public String getBulkheadEnabled() {
        return bulkheadEnabled;
    }

    /**
     * Whether bulkhead is enabled or not on the circuit breaker. Default is false.
     */
    public void setBulkheadEnabled(String bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
    }

    public String getBulkheadMaxConcurrentCalls() {
        return bulkheadMaxConcurrentCalls;
    }

    /**
     * Configures the max amount of concurrent calls the bulkhead will support.
     */
    public void setBulkheadMaxConcurrentCalls(String bulkheadMaxConcurrentCalls) {
        this.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls;
    }

    public String getBulkheadMaxWaitDuration() {
        return bulkheadMaxWaitDuration;
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
    public void setBulkheadMaxWaitDuration(String bulkheadMaxWaitDuration) {
        this.bulkheadMaxWaitDuration = bulkheadMaxWaitDuration;
    }

    public String getTimeoutEnabled() {
        return timeoutEnabled;
    }

    /**
     * Whether timeout is enabled or not on the circuit breaker. Default is false.
     */
    public void setTimeoutEnabled(String timeoutEnabled) {
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
     * Configures the thread execution timeout. Default value is 1 second.
     */
    public void setTimeoutDuration(String timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public String getTimeoutCancelRunningFuture() {
        return timeoutCancelRunningFuture;
    }

    /**
     * Configures whether cancel is called on the running future. Defaults to true.
     */
    public void setTimeoutCancelRunningFuture(String timeoutCancelRunningFuture) {
        this.timeoutCancelRunningFuture = timeoutCancelRunningFuture;
    }
}
