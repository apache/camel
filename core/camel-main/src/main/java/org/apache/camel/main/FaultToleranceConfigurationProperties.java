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

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * Global configuration for MicroProfile Fault Tolerance EIP circuit breaker.
 */
@Configurer(bootstrap = true, extended = true)
public class FaultToleranceConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    private String circuitBreaker;
    @Metadata(defaultValue = "5")
    private Long delay;
    @Metadata(defaultValue = "1")
    private Integer successThreshold;
    @Metadata(defaultValue = "20")
    private Integer requestVolumeThreshold;
    @Metadata(defaultValue = "50")
    private Integer failureRatio;
    @Metadata(defaultValue = "false")
    private Boolean timeoutEnabled;
    @Metadata(defaultValue = "1000")
    private Long timeoutDuration;
    @Metadata(defaultValue = "10")
    private Integer timeoutPoolSize;
    private String timeoutScheduledExecutorService;
    @Metadata(defaultValue = "false")
    private Boolean bulkheadEnabled;
    @Metadata(defaultValue = "10")
    private Integer bulkheadMaxConcurrentCalls;
    @Metadata(defaultValue = "10")
    private Integer bulkheadWaitingTaskQueue;
    private String bulkheadExecutorService;

    public FaultToleranceConfigurationProperties(MainConfigurationProperties parent) {
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
     * Refers to an existing io.smallrye.faulttolerance.core.circuit.breaker.CircuitBreaker instance to lookup and use
     * from the registry. When using this, then any other circuit breaker options are not in use.
     */
    public void setCircuitBreaker(String circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }

    public Long getDelay() {
        return delay;
    }

    /**
     * Control how long the circuit breaker stays open. The value are in seconds and the default is 5 seconds.
     */
    public void setDelay(Long delay) {
        this.delay = delay;
    }

    public Integer getSuccessThreshold() {
        return successThreshold;
    }

    /**
     * Controls the number of trial calls which are allowed when the circuit breaker is half-open Default value is 1.
     */
    public void setSuccessThreshold(Integer successThreshold) {
        this.successThreshold = successThreshold;
    }

    public Integer getRequestVolumeThreshold() {
        return requestVolumeThreshold;
    }

    /**
     * Controls the size of the rolling window used when the circuit breaker is closed Default value is 20.
     */
    public void setRequestVolumeThreshold(Integer requestVolumeThreshold) {
        this.requestVolumeThreshold = requestVolumeThreshold;
    }

    public Integer getFailureRatio() {
        return failureRatio;
    }

    /**
     * Configures the failure rate threshold in percentage. If the failure rate is equal or greater than the threshold
     * the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public void setFailureRatio(Integer failureRatio) {
        this.failureRatio = failureRatio;
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

    public Long getTimeoutDuration() {
        return timeoutDuration;
    }

    /**
     * Configures the thread execution timeout. Default value is 1000 milliseconds.
     */
    public void setTimeoutDuration(Long timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public Integer getTimeoutPoolSize() {
        return timeoutPoolSize;
    }

    /**
     * Configures the pool size of the thread pool when timeout is enabled. Default value is 10.
     */
    public void setTimeoutPoolSize(Integer timeoutPoolSize) {
        this.timeoutPoolSize = timeoutPoolSize;
    }

    public String getTimeoutScheduledExecutorService() {
        return timeoutScheduledExecutorService;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled
     */
    public void setTimeoutScheduledExecutorService(String timeoutScheduledExecutorService) {
        this.timeoutScheduledExecutorService = timeoutScheduledExecutorService;
    }

    public Boolean getBulkheadEnabled() {
        return bulkheadEnabled;
    }

    /**
     * Whether bulkhead is enabled or not on the circuit breaker. Default is false.
     */
    public void setBulkheadEnabled(Boolean bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
    }

    public Integer getBulkheadMaxConcurrentCalls() {
        return bulkheadMaxConcurrentCalls;
    }

    /**
     * Configures the max amount of concurrent calls the bulkhead will support. Default value is 10.
     */
    public void setBulkheadMaxConcurrentCalls(Integer bulkheadMaxConcurrentCalls) {
        this.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls;
    }

    public Integer getBulkheadWaitingTaskQueue() {
        return bulkheadWaitingTaskQueue;
    }

    /**
     * Configures the task queue size for holding waiting tasks to be processed by the bulkhead. Default value is 10.
     */
    public void setBulkheadWaitingTaskQueue(Integer bulkheadWaitingTaskQueue) {
        this.bulkheadWaitingTaskQueue = bulkheadWaitingTaskQueue;
    }

    public String getBulkheadExecutorService() {
        return bulkheadExecutorService;
    }

    /**
     * References to a custom thread pool to use when bulkhead is enabled.
     */
    public void setBulkheadExecutorService(String bulkheadExecutorService) {
        this.bulkheadExecutorService = bulkheadExecutorService;
    }

    /**
     * Refers to an existing io.smallrye.faulttolerance.core.circuit.breaker.CircuitBreaker instance to lookup and use
     * from the registry. When using this, then any other circuit breaker options are not in use.
     */
    public FaultToleranceConfigurationProperties withCircuitBreakerRef(String circuitBreakerRef) {
        this.circuitBreaker = circuitBreakerRef;
        return this;
    }

    /**
     * Control how long the circuit breaker stays open. The value are in seconds and the default is 5 seconds.
     */
    public FaultToleranceConfigurationProperties withDelay(Long delay) {
        this.delay = delay;
        return this;
    }

    /**
     * Controls the number of trial calls which are allowed when the circuit breaker is half-open Default value is 1.
     */
    public FaultToleranceConfigurationProperties withSuccessThreshold(Integer successThreshold) {
        this.successThreshold = successThreshold;
        return this;
    }

    /**
     * Controls the size of the rolling window used when the circuit breaker is closed Default value is 20.
     */
    public FaultToleranceConfigurationProperties withRequestVolumeThreshold(Integer requestVolumeThreshold) {
        this.requestVolumeThreshold = requestVolumeThreshold;
        return this;
    }

    /**
     * Configures the failure rate threshold in percentage. If the failure rate is equal or greater than the threshold
     * the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public FaultToleranceConfigurationProperties withFailureRatio(Integer failureRatio) {
        this.failureRatio = failureRatio;
        return this;
    }

    /**
     * Whether timeout is enabled or not on the circuit breaker. Default is false.
     */
    public FaultToleranceConfigurationProperties withTimeoutEnabled(Boolean timeoutEnabled) {
        this.timeoutEnabled = timeoutEnabled;
        return this;
    }

    /**
     * Configures the thread execution timeout. Default value is 1000 milliseconds.
     */
    public FaultToleranceConfigurationProperties withTimeoutDuration(Long timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
        return this;
    }

    /**
     * Configures the pool size of the thread pool when timeout is enabled. Default value is 10.
     */
    public FaultToleranceConfigurationProperties withTimeoutPoolSize(Integer timeoutPoolSize) {
        this.timeoutPoolSize = timeoutPoolSize;
        return this;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled
     */
    public FaultToleranceConfigurationProperties withTimeoutScheduledExecutorServiceRef(
            String timeoutScheduledExecutorServiceRef) {
        this.timeoutScheduledExecutorService = timeoutScheduledExecutorServiceRef;
        return this;
    }

    /**
     * Whether bulkhead is enabled or not on the circuit breaker. Default is false.
     */
    public FaultToleranceConfigurationProperties withBulkheadEnabled(Boolean bulkheadEnabled) {
        this.bulkheadEnabled = bulkheadEnabled;
        return this;
    }

    /**
     * Configures the max amount of concurrent calls the bulkhead will support. Default value is 10.
     */
    public FaultToleranceConfigurationProperties withBulkheadMaxConcurrentCalls(Integer bulkheadMaxConcurrentCalls) {
        this.bulkheadMaxConcurrentCalls = bulkheadMaxConcurrentCalls;
        return this;
    }

    /**
     * Configures the task queue size for holding waiting tasks to be processed by the bulkhead. Default value is 10.
     */
    public FaultToleranceConfigurationProperties withBulkheadWaitingTaskQueue(Integer bulkheadWaitingTaskQueue) {
        this.bulkheadWaitingTaskQueue = bulkheadWaitingTaskQueue;
        return this;
    }

    /**
     * References to a custom thread pool to use when bulkhead is enabled.
     */
    public FaultToleranceConfigurationProperties withBulkheadExecutorServiceRef(String bulkheadExecutorServiceRef) {
        this.bulkheadExecutorService = bulkheadExecutorServiceRef;
        return this;
    }

}
