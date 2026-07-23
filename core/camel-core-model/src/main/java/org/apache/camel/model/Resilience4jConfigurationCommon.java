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

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

import org.apache.camel.spi.Metadata;

@XmlAccessorType(XmlAccessType.FIELD)
public class Resilience4jConfigurationCommon extends IdentifiedType {

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreaker instance to lookup and use from the registry."
                            + " When using this, then any other circuit breaker options are not in use.")
    private String circuitBreaker;
    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreakerConfig instance to lookup and use from the registry.")
    private String config;
    @XmlAttribute
    @Metadata(defaultValue = "50", javaType = "java.lang.Float",
              description = "Configures the failure rate threshold in percentage."
                            + " If the failure rate is equal or greater than the threshold the CircuitBreaker transitions to open and starts short-circuiting calls.")
    private String failureRateThreshold;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "10", javaType = "java.lang.Integer",
              description = "Configures the number of permitted calls when the CircuitBreaker is half open.")
    private String permittedNumberOfCallsInHalfOpenState;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether to throw io.github.resilience4j.circuitbreaker.CallNotPermittedException when the call is rejected"
                            + " because the circuit breaker is half open or open.")
    private String throwExceptionWhenHalfOpenOrOpenState;
    @XmlAttribute
    @Metadata(defaultValue = "100", javaType = "java.lang.Integer",
              description = "Configures the size of the sliding window which is used to record the outcome of calls when the CircuitBreaker is closed."
                            + " Sliding window can either be count-based or time-based.")
    private String slidingWindowSize;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "COUNT_BASED", enums = "TIME_BASED,COUNT_BASED",
              description = "Configures the type of the sliding window which is used to record the outcome of calls when the CircuitBreaker is closed."
                            + " Sliding window can either be count-based or time-based.")
    private String slidingWindowType;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "SYNCHRONIZED", enums = "LOCK_FREE,SYNCHRONIZED",
              description = "Configures the synchronization strategy for the sliding window."
                            + " LOCK_FREE uses a CAS-based lock-free algorithm for better performance under high concurrency."
                            + " SYNCHRONIZED uses blocking locks with lower memory allocation.")
    private String slidingWindowSynchronizationStrategy;
    @XmlAttribute
    @Metadata(defaultValue = "100", javaType = "java.lang.Integer",
              description = "Configures the minimum number of calls which are required (per sliding window period) before the CircuitBreaker can calculate the error rate.")
    private String minimumNumberOfCalls;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean",
              description = "Enables writable stack traces. When set to false, Exception.getStackTrace returns a zero length array."
                            + " This may be used to reduce log spam when the circuit breaker is open.")
    private String writableStackTraceEnabled;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "60000", javaType = "java.time.Duration",
              description = "Configures the wait duration which specifies how long the CircuitBreaker should stay open, before it switches to half open."
                            + " The default is 60 seconds.")
    private String waitDurationInOpenState;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Enables automatic transition from OPEN to HALF_OPEN state once the waitDurationInOpenState has passed.")
    private String automaticTransitionFromOpenToHalfOpenEnabled;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "0", javaType = "java.time.Duration",
              description = "Configures the maximum wait duration which controls how long the CircuitBreaker should stay in Half Open state,"
                            + " before it switches to open. Value 0 means circuit breaker will wait in half open state"
                            + " until all permitted calls have been completed.")
    private String maxWaitDurationInHalfOpenState;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "100", javaType = "java.lang.Float",
              description = "Configures a threshold in percentage. The CircuitBreaker considers a call as slow when the call duration is greater than slowCallDurationThreshold."
                            + " When the percentage of slow calls is equal or greater the threshold, the CircuitBreaker transitions to open and starts short-circuiting calls.")
    private String slowCallRateThreshold;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "60000", javaType = "java.time.Duration",
              description = "Configures the duration threshold above which calls are considered as slow and increase the slow calls percentage."
                            + " The default is 60 seconds.")
    private String slowCallDurationThreshold;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether bulkhead is enabled or not on the circuit breaker.")
    private String bulkheadEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "25", javaType = "java.lang.Integer",
              description = "Configures the max amount of concurrent calls the bulkhead will support.")
    private String bulkheadMaxConcurrentCalls;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "0", javaType = "java.time.Duration",
              description = "Configures a maximum amount of time which the calling thread will wait to enter the bulkhead."
                            + " The default is 0 (no waiting).")
    private String bulkheadMaxWaitDuration;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean",
              description = "Configures whether the bulkhead uses a fair calling strategy."
                            + " When enabled (default), a fair strategy guarantees the order of incoming requests (FIFO)."
                            + " When disabled, no ordering is guaranteed and may improve throughput.")
    private String bulkheadFairCallHandlingEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether to use asynchronous (non-blocking) processing with CompletionStage-based circuit breaker decorators."
                            + " When enabled, the circuit breaker releases the caller thread immediately and completes processing asynchronously."
                            + " This is most valuable when the downstream processor supports asynchronous processing (e.g. Netty HTTP, Kafka)."
                            + " When used with timeout, the timeoutExecutorService must be a ScheduledExecutorService.")
    private String asynchronous;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether timeout is enabled or not on the circuit breaker.")
    private String timeoutEnabled;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.util.concurrent.ExecutorService",
              description = "References to a custom thread pool to use when timeout is enabled (uses ForkJoinPool.commonPool() by default).")
    private String timeoutExecutorService;
    @XmlAttribute
    @Metadata(defaultValue = "1000", javaType = "java.time.Duration",
              description = "Configures the thread execution timeout. Default value is 1 second.")
    private String timeoutDuration;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean",
              description = "Configures whether cancel is called on the running future. Defaults to true.")
    private String timeoutCancelRunningFuture;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether to enable collecting statistics using Micrometer for all circuit breaker instances."
                            + " This is a global setting (configure via camel.resilience4j.micrometerEnabled=true)"
                            + " and requires adding camel-resilience4j-micrometer JAR to the classpath.")
    private String micrometerEnabled;
    @XmlElement(name = "recordException")
    @Metadata(label = "advanced",
              description = "Configure a list of exceptions that are recorded as a failure and thus increase the failure rate."
                            + " Any exception matching or inheriting from one of the list counts as a failure, unless explicitly ignored via ignoreExceptions.")
    private List<String> recordExceptions = new ArrayList<>();
    @XmlElement(name = "ignoreException")
    @Metadata(label = "advanced",
              description = "Configure a list of exceptions that are ignored and neither count as a failure nor success."
                            + " Any exception matching or inheriting from one of the list will not count as a failure nor success, even if the exception is part of recordExceptions.")
    private List<String> ignoreExceptions = new ArrayList<>();

    public Resilience4jConfigurationCommon() {
    }

    protected Resilience4jConfigurationCommon(Resilience4jConfigurationCommon source) {
        this.circuitBreaker = source.circuitBreaker;
        this.config = source.config;
        this.failureRateThreshold = source.failureRateThreshold;
        this.permittedNumberOfCallsInHalfOpenState = source.permittedNumberOfCallsInHalfOpenState;
        this.throwExceptionWhenHalfOpenOrOpenState = source.throwExceptionWhenHalfOpenOrOpenState;
        this.slidingWindowSize = source.slidingWindowSize;
        this.slidingWindowType = source.slidingWindowType;
        this.slidingWindowSynchronizationStrategy = source.slidingWindowSynchronizationStrategy;
        this.minimumNumberOfCalls = source.minimumNumberOfCalls;
        this.writableStackTraceEnabled = source.writableStackTraceEnabled;
        this.waitDurationInOpenState = source.waitDurationInOpenState;
        this.automaticTransitionFromOpenToHalfOpenEnabled = source.automaticTransitionFromOpenToHalfOpenEnabled;
        this.maxWaitDurationInHalfOpenState = source.maxWaitDurationInHalfOpenState;
        this.slowCallRateThreshold = source.slowCallRateThreshold;
        this.slowCallDurationThreshold = source.slowCallDurationThreshold;
        this.bulkheadEnabled = source.bulkheadEnabled;
        this.bulkheadMaxConcurrentCalls = source.bulkheadMaxConcurrentCalls;
        this.bulkheadMaxWaitDuration = source.bulkheadMaxWaitDuration;
        this.bulkheadFairCallHandlingEnabled = source.bulkheadFairCallHandlingEnabled;
        this.asynchronous = source.asynchronous;
        this.timeoutEnabled = source.timeoutEnabled;
        this.micrometerEnabled = source.micrometerEnabled;
        this.timeoutExecutorService = source.timeoutExecutorService;
        this.timeoutDuration = source.timeoutDuration;
        this.timeoutCancelRunningFuture = source.timeoutCancelRunningFuture;
        this.recordExceptions = new ArrayList<>(source.ignoreExceptions);
        this.ignoreExceptions = new ArrayList<>(source.ignoreExceptions);
    }

    public Resilience4jConfigurationCommon copyDefinition() {
        return new Resilience4jConfigurationCommon(this);
    }

    // Getter/Setter
    // -------------------------------------------------------------------------

    public String getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(String circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getFailureRateThreshold() {
        return failureRateThreshold;
    }

    public void setFailureRateThreshold(String failureRateThreshold) {
        this.failureRateThreshold = failureRateThreshold;
    }

    public String getPermittedNumberOfCallsInHalfOpenState() {
        return permittedNumberOfCallsInHalfOpenState;
    }

    public void setPermittedNumberOfCallsInHalfOpenState(String permittedNumberOfCallsInHalfOpenState) {
        this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
    }

    public String getThrowExceptionWhenHalfOpenOrOpenState() {
        return throwExceptionWhenHalfOpenOrOpenState;
    }

    public void setThrowExceptionWhenHalfOpenOrOpenState(String throwExceptionWhenHalfOpenOrOpenState) {
        this.throwExceptionWhenHalfOpenOrOpenState = throwExceptionWhenHalfOpenOrOpenState;
    }

    public String getSlidingWindowSize() {
        return slidingWindowSize;
    }

    public void setSlidingWindowSize(String slidingWindowSize) {
        this.slidingWindowSize = slidingWindowSize;
    }

    public String getSlidingWindowType() {
        return slidingWindowType;
    }

    public void setSlidingWindowType(String slidingWindowType) {
        this.slidingWindowType = slidingWindowType;
    }

    public String getSlidingWindowSynchronizationStrategy() {
        return slidingWindowSynchronizationStrategy;
    }

    public void setSlidingWindowSynchronizationStrategy(String slidingWindowSynchronizationStrategy) {
        this.slidingWindowSynchronizationStrategy = slidingWindowSynchronizationStrategy;
    }

    public String getMinimumNumberOfCalls() {
        return minimumNumberOfCalls;
    }

    public void setMinimumNumberOfCalls(String minimumNumberOfCalls) {
        this.minimumNumberOfCalls = minimumNumberOfCalls;
    }

    public String getWritableStackTraceEnabled() {
        return writableStackTraceEnabled;
    }

    public void setWritableStackTraceEnabled(String writableStackTraceEnabled) {
        this.writableStackTraceEnabled = writableStackTraceEnabled;
    }

    public String getWaitDurationInOpenState() {
        return waitDurationInOpenState;
    }

    public void setWaitDurationInOpenState(String waitDurationInOpenState) {
        this.waitDurationInOpenState = waitDurationInOpenState;
    }

    public String getAutomaticTransitionFromOpenToHalfOpenEnabled() {
        return automaticTransitionFromOpenToHalfOpenEnabled;
    }

    public void setAutomaticTransitionFromOpenToHalfOpenEnabled(String automaticTransitionFromOpenToHalfOpenEnabled) {
        this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
    }

    public String getMaxWaitDurationInHalfOpenState() {
        return maxWaitDurationInHalfOpenState;
    }

    public void setMaxWaitDurationInHalfOpenState(String maxWaitDurationInHalfOpenState) {
        this.maxWaitDurationInHalfOpenState = maxWaitDurationInHalfOpenState;
    }

    public String getSlowCallRateThreshold() {
        return slowCallRateThreshold;
    }

    public void setSlowCallRateThreshold(String slowCallRateThreshold) {
        this.slowCallRateThreshold = slowCallRateThreshold;
    }

    public String getSlowCallDurationThreshold() {
        return slowCallDurationThreshold;
    }

    public void setSlowCallDurationThreshold(String slowCallDurationThreshold) {
        this.slowCallDurationThreshold = slowCallDurationThreshold;
    }

    public String getBulkheadEnabled() {
        return bulkheadEnabled;
    }

    public void setBulkheadEnabled(String bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
    }

    public String getBulkheadMaxConcurrentCalls() {
        return bulkheadMaxConcurrentCalls;
    }

    public void setBulkheadMaxConcurrentCalls(String bulkheadMaxConcurrentCalls) {
        this.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls;
    }

    public String getBulkheadMaxWaitDuration() {
        return bulkheadMaxWaitDuration;
    }

    public void setBulkheadMaxWaitDuration(String bulkheadMaxWaitDuration) {
        this.bulkheadMaxWaitDuration = bulkheadMaxWaitDuration;
    }

    public String getBulkheadFairCallHandlingEnabled() {
        return bulkheadFairCallHandlingEnabled;
    }

    public void setBulkheadFairCallHandlingEnabled(String bulkheadFairCallHandlingEnabled) {
        this.bulkheadFairCallHandlingEnabled = bulkheadFairCallHandlingEnabled;
    }

    public String getAsynchronous() {
        return asynchronous;
    }

    public void setAsynchronous(String asynchronous) {
        this.asynchronous = asynchronous;
    }

    public String getTimeoutEnabled() {
        return timeoutEnabled;
    }

    public void setTimeoutEnabled(String timeoutEnabled) {
        this.timeoutEnabled = timeoutEnabled;
    }

    public String getTimeoutExecutorService() {
        return timeoutExecutorService;
    }

    public void setTimeoutExecutorService(String timeoutExecutorService) {
        this.timeoutExecutorService = timeoutExecutorService;
    }

    public String getTimeoutDuration() {
        return timeoutDuration;
    }

    public void setTimeoutDuration(String timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public String getTimeoutCancelRunningFuture() {
        return timeoutCancelRunningFuture;
    }

    public void setTimeoutCancelRunningFuture(String timeoutCancelRunningFuture) {
        this.timeoutCancelRunningFuture = timeoutCancelRunningFuture;
    }

    public String getMicrometerEnabled() {
        return micrometerEnabled;
    }

    public void setMicrometerEnabled(String micrometerEnabled) {
        this.micrometerEnabled = micrometerEnabled;
    }

    public List<String> getRecordExceptions() {
        return recordExceptions;
    }

    public void setRecordExceptions(List<String> recordExceptions) {
        this.recordExceptions = recordExceptions;
    }

    public List<String> getIgnoreExceptions() {
        return ignoreExceptions;
    }

    public void setIgnoreExceptions(List<String> ignoreExceptions) {
        this.ignoreExceptions = ignoreExceptions;
    }
}
