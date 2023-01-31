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

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;

/**
 * MicroProfile Fault Tolerance Circuit Breaker EIP configuration
 */
@Metadata(label = "configuration,eip")
@XmlRootElement(name = "faultToleranceConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
@Configurer(extended = true)
public class FaultToleranceConfigurationDefinition extends FaultToleranceConfigurationCommon {

    @XmlTransient
    private CircuitBreakerDefinition parent;

    public FaultToleranceConfigurationDefinition() {
    }

    public FaultToleranceConfigurationDefinition(CircuitBreakerDefinition parent) {
        this.parent = parent;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Refers to an existing io.github.resilience4j.circuitbreaker.CircuitBreaker instance to lookup and use from the
     * registry. When using this, then any other circuit breaker options are not in use.
     */
    public FaultToleranceConfigurationDefinition circuitBreaker(String circuitBreaker) {
        setCircuitBreaker(circuitBreaker);
        return this;
    }

    /**
     * Control how long the circuit breaker stays open. The default is 5 seconds.
     */
    public FaultToleranceConfigurationDefinition delay(long delay) {
        setDelay(Long.toString(delay));
        return this;
    }

    /**
     * Control how long the circuit breaker stays open. The default is 5 seconds.
     */
    public FaultToleranceConfigurationDefinition delay(String delay) {
        setDelay(delay);
        return this;
    }

    /**
     * Controls the number of trial calls which are allowed when the circuit breaker is half-open
     */
    public FaultToleranceConfigurationDefinition successThreshold(int successThreshold) {
        setSuccessThreshold(Integer.toString(successThreshold));
        return this;
    }

    /**
     * Controls the size of the rolling window used when the circuit breaker is closed
     */
    public FaultToleranceConfigurationDefinition requestVolumeThreshold(int requestVolumeThreshold) {
        setRequestVolumeThreshold(Integer.toString(requestVolumeThreshold));
        return this;
    }

    /**
     * Configures the failure rate threshold in percentage. If the failure rate is equal or greater than the threshold
     * the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public FaultToleranceConfigurationDefinition failureRatio(int failureRatio) {
        setFailureRatio(Integer.toString(failureRatio));
        return this;
    }

    /**
     * Whether timeout is enabled or not on the circuit breaker. Default is false.
     */
    public FaultToleranceConfigurationDefinition timeoutEnabled(boolean timeoutEnabled) {
        setTimeoutEnabled(Boolean.toString(timeoutEnabled));
        return this;
    }

    /**
     * Configures the thread execution timeout. Default value is 1 second.
     */
    public FaultToleranceConfigurationDefinition timeoutDuration(long timeoutDuration) {
        setTimeoutDuration(Long.toString(timeoutDuration));
        return this;
    }

    /**
     * Configures the thread execution timeout. Default value is 1 second.
     */
    public FaultToleranceConfigurationDefinition timeoutDuration(String timeoutDuration) {
        setTimeoutDuration(timeoutDuration);
        return this;
    }

    /**
     * Configures the pool size of the thread pool when timeout is enabled. Default value is 10.
     */
    public FaultToleranceConfigurationDefinition timeoutPoolSize(int poolSize) {
        setTimeoutPoolSize(Integer.toString(poolSize));
        return this;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled
     */
    public FaultToleranceConfigurationDefinition timeoutScheduledExecutorService(String executorService) {
        setTimeoutScheduledExecutorService(executorService);
        return this;
    }

    /**
     * Whether bulkhead is enabled or not on the circuit breaker. Default is false.
     */
    public FaultToleranceConfigurationDefinition bulkheadEnabled(boolean bulkheadEnabled) {
        setBulkheadEnabled(Boolean.toString(bulkheadEnabled));
        return this;
    }

    /**
     * Configures the max amount of concurrent calls the bulkhead will support.
     */
    public FaultToleranceConfigurationDefinition bulkheadMaxConcurrentCalls(int bulkheadMaxConcurrentCalls) {
        setBulkheadMaxConcurrentCalls(Integer.toString(bulkheadMaxConcurrentCalls));
        return this;
    }

    /**
     * Configures the task queue size for holding waiting tasks to be processed by the bulkhead
     */
    public FaultToleranceConfigurationDefinition bulkheadWaitingTaskQueue(int bulkheadWaitingTaskQueue) {
        setBulkheadWaitingTaskQueue(Integer.toString(bulkheadWaitingTaskQueue));
        return this;
    }

    /**
     * References to a custom thread pool to use when bulkhead is enabled
     */
    public FaultToleranceConfigurationDefinition bulkheadExecutorService(String executorService) {
        setBulkheadExecutorService(executorService);
        return this;
    }

    /**
     * End of configuration.
     */
    public CircuitBreakerDefinition end() {
        return parent;
    }

}
