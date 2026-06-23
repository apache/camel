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
import jakarta.xml.bind.annotation.XmlAttribute;

import org.apache.camel.spi.Metadata;

@XmlAccessorType(XmlAccessType.FIELD)
public class FaultToleranceConfigurationCommon extends IdentifiedType {

    @XmlAttribute
    @Metadata(label = "advanced",
              description = "Refers to an existing io.smallrye.faulttolerance.api.TypedGuard instance to lookup and use from the registry."
                            + " When using this, then any other TypedGuard circuit breaker options are not in use.")
    private String typedGuard;
    @XmlAttribute
    @Metadata(defaultValue = "5000", javaType = "java.time.Duration",
              description = "Control how long the circuit breaker stays open. The default is 5 seconds.")
    private String delay;
    @XmlAttribute
    @Metadata(defaultValue = "1", javaType = "java.lang.Integer",
              description = "Controls the number of trial calls which are allowed when the circuit breaker is half-open.")
    private String successThreshold;
    @XmlAttribute
    @Metadata(defaultValue = "20", javaType = "java.lang.Integer",
              description = "Controls the size of the rolling window used when the circuit breaker is closed.")
    private String requestVolumeThreshold;
    @XmlAttribute
    @Metadata(defaultValue = "50", javaType = "java.lang.Integer",
              description = "Configures the failure rate threshold in percentage."
                            + " If the failure rate is equal or greater than the threshold the CircuitBreaker transitions to open and starts short-circuiting calls.")
    private String failureRatio;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether timeout is enabled or not on the circuit breaker.")
    private String timeoutEnabled;
    @XmlAttribute
    @Metadata(defaultValue = "1000", javaType = "java.time.Duration",
              description = "Configures the thread execution timeout. Default value is 1 second.")
    private String timeoutDuration;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "10", javaType = "java.lang.Integer",
              description = "Configures the pool size of the thread pool when timeout is enabled.")
    private String timeoutPoolSize;
    @XmlAttribute
    @Metadata(defaultValue = "false", javaType = "java.lang.Boolean",
              description = "Whether bulkhead is enabled or not on the circuit breaker.")
    private String bulkheadEnabled;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "10", javaType = "java.lang.Integer",
              description = "Configures the max amount of concurrent calls the bulkhead will support.")
    private String bulkheadMaxConcurrentCalls;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "10", javaType = "java.lang.Integer",
              description = "Configures the task queue size for holding waiting tasks to be processed by the bulkhead.")
    private String bulkheadWaitingTaskQueue;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.util.concurrent.ExecutorService",
              description = "References a custom thread pool to use when offloading a guarded action to another thread.")
    private String threadOffloadExecutorService;

    public FaultToleranceConfigurationCommon() {
    }

    protected FaultToleranceConfigurationCommon(FaultToleranceConfigurationCommon source) {
        this.typedGuard = source.typedGuard;
        this.delay = source.delay;
        this.successThreshold = source.successThreshold;
        this.requestVolumeThreshold = source.requestVolumeThreshold;
        this.failureRatio = source.failureRatio;
        this.timeoutEnabled = source.timeoutEnabled;
        this.timeoutDuration = source.timeoutDuration;
        this.timeoutPoolSize = source.timeoutPoolSize;
        this.bulkheadEnabled = source.bulkheadEnabled;
        this.bulkheadMaxConcurrentCalls = source.bulkheadMaxConcurrentCalls;
        this.threadOffloadExecutorService = source.threadOffloadExecutorService;
    }

    public FaultToleranceConfigurationCommon copyDefinition() {
        return new FaultToleranceConfigurationCommon(this);
    }

    // Getter/Setter
    // -------------------------------------------------------------------------

    public String getTypedGuard() {
        return typedGuard;
    }

    public void setTypedGuard(String typedGuard) {
        this.typedGuard = typedGuard;
    }

    public String getDelay() {
        return delay;
    }

    public void setDelay(String delay) {
        this.delay = delay;
    }

    public String getSuccessThreshold() {
        return successThreshold;
    }

    public void setSuccessThreshold(String successThreshold) {
        this.successThreshold = successThreshold;
    }

    public String getRequestVolumeThreshold() {
        return requestVolumeThreshold;
    }

    public void setRequestVolumeThreshold(String requestVolumeThreshold) {
        this.requestVolumeThreshold = requestVolumeThreshold;
    }

    public String getFailureRatio() {
        return failureRatio;
    }

    public void setFailureRatio(String failureRatio) {
        this.failureRatio = failureRatio;
    }

    public String getTimeoutEnabled() {
        return timeoutEnabled;
    }

    public void setTimeoutEnabled(String timeoutEnabled) {
        this.timeoutEnabled = timeoutEnabled;
    }

    public String getTimeoutDuration() {
        return timeoutDuration;
    }

    public void setTimeoutDuration(String timeoutDuration) {
        this.timeoutDuration = timeoutDuration;
    }

    public String getTimeoutPoolSize() {
        return timeoutPoolSize;
    }

    public void setTimeoutPoolSize(String timeoutPoolSize) {
        this.timeoutPoolSize = timeoutPoolSize;
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

    public String getBulkheadWaitingTaskQueue() {
        return bulkheadWaitingTaskQueue;
    }

    public void setBulkheadWaitingTaskQueue(String bulkheadWaitingTaskQueue) {
        this.bulkheadWaitingTaskQueue = bulkheadWaitingTaskQueue;
    }

    public String getThreadOffloadExecutorService() {
        return threadOffloadExecutorService;
    }

    public void setThreadOffloadExecutorService(String threadOffloadExecutorService) {
        this.threadOffloadExecutorService = threadOffloadExecutorService;
    }
}
