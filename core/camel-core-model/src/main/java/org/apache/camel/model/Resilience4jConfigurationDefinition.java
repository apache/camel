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
@Metadata(label = "configuration,eip,error,resilience",
          description = "Configures Resilience4j settings for the Circuit Breaker EIP, such as failure rate threshold, wait duration, and sliding window parameters")
@XmlRootElement(name = "resilience4jConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer(extended = true)
public class Resilience4jConfigurationDefinition extends Resilience4jConfigurationCommon {

    @XmlTransient
    private CircuitBreakerDefinition parent;

    public Resilience4jConfigurationDefinition() {
    }

    public Resilience4jConfigurationDefinition(Resilience4jConfigurationDefinition source) {
        super(source);
        this.parent = source.parent;
    }

    public Resilience4jConfigurationDefinition(CircuitBreakerDefinition parent) {
        this.parent = parent;
    }

    public Resilience4jConfigurationDefinition copyDefinition() {
        return new Resilience4jConfigurationDefinition(this);
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
     * Configures the failure rate threshold in percentage. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition failureRateThreshold(String failureRateThreshold) {
        setFailureRateThreshold(failureRateThreshold);
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
     * Configures the number of permitted calls when the CircuitBreaker is half open. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition permittedNumberOfCallsInHalfOpenState(
            String permittedNumberOfCallsInHalfOpenState) {
        setPermittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState);
        return this;
    }

    /**
     * Whether to throw io.github.resilience4j.circuitbreaker.CallNotPermittedException when the call is rejected due
     * circuit breaker is half open (and was not attempted but rejected immediately) or open (always rejected).
     *
     * This option is only in use when there is NOT a fallback configured on the circuit breaker. When there is a
     * fallback then the fallback is always executed and CallNotPermittedException is not thrown.
     */
    public Resilience4jConfigurationDefinition throwExceptionWhenHalfOpenOrOpenState(
            boolean throwExceptionWhenHalfOpenOrOpenState) {
        setThrowExceptionWhenHalfOpenOrOpenState(Boolean.toString(throwExceptionWhenHalfOpenOrOpenState));
        return this;
    }

    /**
     * Whether to throw CallNotPermittedException when the call is rejected. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition throwExceptionWhenHalfOpenOrOpenState(
            String throwExceptionWhenHalfOpenOrOpenState) {
        setThrowExceptionWhenHalfOpenOrOpenState(throwExceptionWhenHalfOpenOrOpenState);
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
     * Configures the size of the sliding window. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition slidingWindowSize(String slidingWindowSize) {
        setSlidingWindowSize(slidingWindowSize);
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
     * Configures the synchronization strategy for the sliding window. LOCK_FREE uses a CAS-based lock-free algorithm
     * for better performance under high concurrency. SYNCHRONIZED uses blocking locks with lower memory allocation.
     *
     * Default is SYNCHRONIZED.
     */
    public Resilience4jConfigurationDefinition slidingWindowSynchronizationStrategy(
            String slidingWindowSynchronizationStrategy) {
        setSlidingWindowSynchronizationStrategy(slidingWindowSynchronizationStrategy);
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
     * Configures the minimum number of calls required before the CircuitBreaker can calculate the error rate. Supports
     * property placeholders.
     */
    public Resilience4jConfigurationDefinition minimumNumberOfCalls(String minimumNumberOfCalls) {
        setMinimumNumberOfCalls(minimumNumberOfCalls);
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
     * Enables writable stack traces. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition writableStackTraceEnabled(String writableStackTraceEnabled) {
        setWritableStackTraceEnabled(writableStackTraceEnabled);
        return this;
    }

    /**
     * Configures the wait duration (in millis) which specifies how long the CircuitBreaker should stay open, before it
     * switches to half open. Default value is 60 seconds (60000 millis).
     *
     * @deprecated Use {@link #waitDurationInOpenState(String)} with a Camel duration expression (e.g. "60s", "1m").
     */
    @Deprecated(since = "4.22", forRemoval = true)
    public Resilience4jConfigurationDefinition waitDurationInOpenState(int waitDurationInOpenState) {
        setWaitDurationInOpenState(Integer.toString(waitDurationInOpenState));
        return this;
    }

    /**
     * Configures the wait duration in open state. Accepts a Camel duration (e.g. 60s, 1m, 60000) or ISO-8601 format
     * (e.g. PT1M). Default value is 60 seconds. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition waitDurationInOpenState(String waitDurationInOpenState) {
        setWaitDurationInOpenState(waitDurationInOpenState);
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
     * Enables automatic transition from OPEN to HALF_OPEN state. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition automaticTransitionFromOpenToHalfOpenEnabled(
            String automaticTransitionFromOpenToHalfOpenEnabled) {
        setAutomaticTransitionFromOpenToHalfOpenEnabled(automaticTransitionFromOpenToHalfOpenEnabled);
        return this;
    }

    /**
     * Configures the maximum wait duration (in millis) which controls how long the CircuitBreaker should stay in Half
     * Open state, before it switches to open. Value 0 means circuit breaker will wait in half open state until all
     * permitted calls have been completed.
     */
    public Resilience4jConfigurationDefinition maxWaitDurationInHalfOpenState(int maxWaitDurationInHalfOpenState) {
        setMaxWaitDurationInHalfOpenState(Integer.toString(maxWaitDurationInHalfOpenState));
        return this;
    }

    /**
     * Configures the maximum wait duration which controls how long the CircuitBreaker should stay in Half Open state.
     * Accepts a Camel duration (e.g. 30s, 1m, 30000) or ISO-8601 format (e.g. PT30S). Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition maxWaitDurationInHalfOpenState(String maxWaitDurationInHalfOpenState) {
        setMaxWaitDurationInHalfOpenState(maxWaitDurationInHalfOpenState);
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
     * Configures a threshold in percentage for slow calls. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition slowCallRateThreshold(String slowCallRateThreshold) {
        setSlowCallRateThreshold(slowCallRateThreshold);
        return this;
    }

    /**
     * Configures the duration threshold (in millis) above which calls are considered as slow and increase the slow
     * calls percentage. Default value is 60 seconds (60000 millis).
     *
     * @deprecated Use {@link #slowCallDurationThreshold(String)} with a Camel duration expression (e.g. "60s", "1m").
     */
    @Deprecated(since = "4.22", forRemoval = true)
    public Resilience4jConfigurationDefinition slowCallDurationThreshold(int slowCallDurationThreshold) {
        setSlowCallDurationThreshold(Integer.toString(slowCallDurationThreshold));
        return this;
    }

    /**
     * Configures the duration threshold above which calls are considered as slow. Accepts a Camel duration (e.g. 60s,
     * 1m, 60000) or ISO-8601 format (e.g. PT1M). Default value is 60 seconds. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition slowCallDurationThreshold(String slowCallDurationThreshold) {
        setSlowCallDurationThreshold(slowCallDurationThreshold);
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
     * Whether bulkhead is enabled or not on the circuit breaker. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition bulkheadEnabled(String bulkheadEnabled) {
        setBulkheadEnabled(bulkheadEnabled);
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
     * Configures the max amount of concurrent calls the bulkhead will support. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition bulkheadMaxConcurrentCalls(String bulkheadMaxConcurrentCalls) {
        setBulkheadMaxConcurrentCalls(bulkheadMaxConcurrentCalls);
        return this;
    }

    /**
     * Configures a maximum amount of time (in millis) which the calling thread will wait to enter the bulkhead. If
     * bulkhead has space available, entry is guaranteed and immediate. If bulkhead is full, calling threads will
     * contest for space, if it becomes available. maxWaitDuration can be set to 0.
     * <p>
     * Note: for threads running on an event-loop or equivalent (rx computation pool, etc), setting maxWaitDuration to 0
     * is highly recommended. Blocking an event-loop thread will most likely have a negative effect on application
     * throughput.
     *
     * @deprecated Use {@link #bulkheadMaxWaitDuration(String)} with a Camel duration expression (e.g. "500ms", "5s").
     */
    @Deprecated(since = "4.22", forRemoval = true)
    public Resilience4jConfigurationDefinition bulkheadMaxWaitDuration(int bulkheadMaxWaitDuration) {
        setBulkheadMaxWaitDuration(Integer.toString(bulkheadMaxWaitDuration));
        return this;
    }

    /**
     * Configures a maximum amount of time for the calling thread to wait to enter the bulkhead. Accepts a Camel
     * duration (e.g. 500ms, 5s, 5000) or ISO-8601 format (e.g. PT5S). Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition bulkheadMaxWaitDuration(String bulkheadMaxWaitDuration) {
        setBulkheadMaxWaitDuration(bulkheadMaxWaitDuration);
        return this;
    }

    /**
     * Configures whether the bulkhead uses a fair calling strategy. When enabled (default), a fair strategy guarantees
     * the order of incoming requests (FIFO). When disabled, no ordering is guaranteed and may improve throughput.
     */
    public Resilience4jConfigurationDefinition bulkheadFairCallHandlingEnabled(boolean bulkheadFairCallHandlingEnabled) {
        setBulkheadFairCallHandlingEnabled(Boolean.toString(bulkheadFairCallHandlingEnabled));
        return this;
    }

    /**
     * Configures whether the bulkhead uses a fair calling strategy. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition bulkheadFairCallHandlingEnabled(String bulkheadFairCallHandlingEnabled) {
        setBulkheadFairCallHandlingEnabled(bulkheadFairCallHandlingEnabled);
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
     * Whether timeout is enabled or not on the circuit breaker. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition timeoutEnabled(String timeoutEnabled) {
        setTimeoutEnabled(timeoutEnabled);
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
     * Configures the thread execution timeout (in millis). Default value is 1000 millis (1 second).
     *
     * @deprecated Use {@link #timeoutDuration(String)} with a Camel duration expression (e.g. "1s", "500ms").
     */
    @Deprecated(since = "4.22", forRemoval = true)
    public Resilience4jConfigurationDefinition timeoutDuration(int timeoutDuration) {
        setTimeoutDuration(Integer.toString(timeoutDuration));
        return this;
    }

    /**
     * Configures the thread execution timeout. Accepts a Camel duration (e.g. 1s, 1000, 500ms) or ISO-8601 format (e.g.
     * PT1S). Default value is 1 second. Supports property placeholders.
     */
    public Resilience4jConfigurationDefinition timeoutDuration(String timeoutDuration) {
        setTimeoutDuration(timeoutDuration);
        return this;
    }

    /**
     * Whether to enable collecting statistics using Micrometer for all circuit breaker instances. This is a global
     * setting (configure via camel.resilience4j.micrometerEnabled=true) and requires adding
     * camel-resilience4j-micrometer JAR to the classpath.
     */
    public Resilience4jConfigurationDefinition micrometerEnabled(boolean micrometerEnabled) {
        setMicrometerEnabled(Boolean.toString(micrometerEnabled));
        return this;
    }

    /**
     * Whether to enable collecting statistics using Micrometer for all circuit breaker instances. Supports property
     * placeholders.
     */
    public Resilience4jConfigurationDefinition micrometerEnabled(String micrometerEnabled) {
        setMicrometerEnabled(micrometerEnabled);
        return this;
    }

    /**
     * Configure a list of exceptions that are recorded as a failure and thus increase the failure rate. Any exception
     * matching or inheriting from one of the list counts as a failure, unless explicitly ignored via ignoreExceptions.
     */
    public Resilience4jConfigurationDefinition recordException(Throwable... exception) {
        for (Throwable t : exception) {
            getRecordExceptions().add(t.getClass().getName());
        }
        return this;
    }

    /**
     * Configure a list of exceptions that are recorded as a failure and thus increase the failure rate. Any exception
     * matching or inheriting from one of the list counts as a failure, unless explicitly ignored via ignoreExceptions.
     */
    @SafeVarargs
    public final Resilience4jConfigurationDefinition recordException(Class<? extends Throwable>... exception) {
        for (Class<? extends Throwable> t : exception) {
            getRecordExceptions().add(t.getName());
        }
        return this;
    }

    /**
     * Configure a list of exceptions that are ignored and neither count as a failure nor success. Any exception
     * matching or inheriting from one of the list will not count as a failure nor success, even if the exceptions is
     * part of recordExceptions.
     */
    public Resilience4jConfigurationDefinition ignoreException(Throwable... exception) {
        for (Throwable t : exception) {
            getIgnoreExceptions().add(t.getClass().getName());
        }
        return this;
    }

    /**
     * Configure a list of exceptions that are ignored and neither count as a failure nor success. Any exception
     * matching or inheriting from one of the list will not count as a failure nor success, even if the exceptions is
     * part of recordExceptions.
     */
    @SafeVarargs
    public final Resilience4jConfigurationDefinition ignoreException(Class<? extends Throwable>... exception) {
        for (Class<? extends Throwable> t : exception) {
            getIgnoreExceptions().add(t.getName());
        }
        return this;
    }

    /**
     * End of configuration.
     */
    public CircuitBreakerDefinition end() {
        return parent;
    }

}
