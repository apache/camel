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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;

import org.apache.camel.spi.Metadata;

@XmlAccessorType(XmlAccessType.FIELD)
public class FaultToleranceConfigurationCommon extends IdentifiedType {

    @XmlAttribute
    @Metadata(label = "circuitbreaker")
    private String circuitBreakerRef;
    @XmlAttribute
    @Metadata(label = "circuitbreaker", defaultValue = "5s", javaType = "java.time.Duration")
    private String delay;
    @XmlAttribute
    @Metadata(label = "circuitbreaker", defaultValue = "1", javaType = "java.lang.Integer")
    private String successThreshold;
    @XmlAttribute
    @Metadata(label = "circuitbreaker", defaultValue = "20", javaType = "java.lang.Integer")
    private String requestVolumeThreshold;
    @XmlAttribute
    @Metadata(label = "circuitbreaker", defaultValue = "50", javaType = "java.lang.Integer")
    private String failureRatio;
    @XmlAttribute
    @Metadata(label = "timeout", defaultValue = "false", javaType = "java.lang.Boolean")
    private String timeoutEnabled;
    @XmlAttribute
    @Metadata(label = "timeout", defaultValue = "1s", javaType = "java.time.Duration")
    private String timeoutDuration;
    @XmlAttribute
    @Metadata(label = "timeout", defaultValue = "10", javaType = "java.lang.Integer")
    private String timeoutPoolSize;
    @XmlAttribute
    @Metadata(label = "timeout")
    private String timeoutScheduledExecutorServiceRef;
    @XmlAttribute
    @Metadata(label = "bulkhead", defaultValue = "false", javaType = "java.lang.Boolean")
    private String bulkheadEnabled;
    @XmlAttribute
    @Metadata(label = "bulkhead", defaultValue = "10", javaType = "java.lang.Integer")
    private String bulkheadMaxConcurrentCalls;
    @XmlAttribute
    @Metadata(label = "bulkhead", defaultValue = "10", javaType = "java.lang.Integer")
    private String bulkheadWaitingTaskQueue;
    @XmlAttribute
    @Metadata(label = "bulkhead")
    private String bulkheadExecutorServiceRef;

    // Getter/Setter
    // -------------------------------------------------------------------------

    public String getCircuitBreakerRef() {
        return circuitBreakerRef;
    }

    /**
     * Refers to an existing io.smallrye.faulttolerance.core.circuit.breaker.CircuitBreaker instance to lookup and use
     * from the registry. When using this, then any other circuit breaker options are not in use.
     */
    public void setCircuitBreakerRef(String circuitBreakerRef) {
        this.circuitBreakerRef = circuitBreakerRef;
    }

    public String getDelay() {
        return delay;
    }

    /**
     * Control how long the circuit breaker stays open. The default is 5 seconds.
     */
    public void setDelay(String delay) {
        this.delay = delay;
    }

    public String getSuccessThreshold() {
        return successThreshold;
    }

    /**
     * Controls the number of trial calls which are allowed when the circuit breaker is half-open
     */
    public void setSuccessThreshold(String successThreshold) {
        this.successThreshold = successThreshold;
    }

    public String getRequestVolumeThreshold() {
        return requestVolumeThreshold;
    }

    /**
     * Controls the size of the rolling window used when the circuit breaker is closed
     */
    public void setRequestVolumeThreshold(String requestVolumeThreshold) {
        this.requestVolumeThreshold = requestVolumeThreshold;
    }

    public String getFailureRatio() {
        return failureRatio;
    }

    /**
     * Configures the failure rate threshold in percentage. If the failure rate is equal or greater than the threshold
     * the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public void setFailureRatio(String failureRatio) {
        this.failureRatio = failureRatio;
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

    public String getTimeoutDuration() {
        return timeoutDuration;
    }

    /**
     * Configures the thread execution timeout. Default value is 1 second.
     */
    public void setTimeoutDuration(String timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public String getTimeoutPoolSize() {
        return timeoutPoolSize;
    }

    /**
     * Configures the pool size of the thread pool when timeout is enabled. Default value is 10.
     */
    public void setTimeoutPoolSize(String timeoutPoolSize) {
        this.timeoutPoolSize = timeoutPoolSize;
    }

    public String getTimeoutScheduledExecutorServiceRef() {
        return timeoutScheduledExecutorServiceRef;
    }

    /**
     * References to a custom thread pool to use when timeout is enabled
     */
    public void setTimeoutScheduledExecutorServiceRef(String timeoutScheduledExecutorServiceRef) {
        this.timeoutScheduledExecutorServiceRef = timeoutScheduledExecutorServiceRef;
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

    public String getBulkheadWaitingTaskQueue() {
        return bulkheadWaitingTaskQueue;
    }

    /**
     * Configures the task queue size for holding waiting tasks to be processed by the bulkhead.
     */
    public void setBulkheadWaitingTaskQueue(String bulkheadWaitingTaskQueue) {
        this.bulkheadWaitingTaskQueue = bulkheadWaitingTaskQueue;
    }

    public String getBulkheadExecutorServiceRef() {
        return bulkheadExecutorServiceRef;
    }

    /**
     * References to a custom thread pool to use when bulkhead is enabled.
     */
    public void setBulkheadExecutorServiceRef(String bulkheadExecutorServiceRef) {
        this.bulkheadExecutorServiceRef = bulkheadExecutorServiceRef;
    }
}
