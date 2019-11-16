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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;

/**
 * Resilience4j Circuit Breaker EIP configuration
 */
@Metadata(label = "eip,routing,circuitbreaker")
@XmlRootElement(name = "resilience4jConfiguration")
@XmlAccessorType(XmlAccessType.FIELD)
public class Resilience4jConfigurationDefinition extends Resilience4jConfigurationCommon {

    public static final String DEFAULT_GROUP_KEY = "Camel";

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
     * Sets the group key to use. The default value is Camel.
     */
    public Resilience4jConfigurationDefinition groupKey(String groupKey) {
        setGroupKey(groupKey);
        return this;
    }

    /**
     * Configures the failure rate threshold in percentage.
     * If the failure rate is equal or greater than the threshold the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100. Default value is 50 percentage.
     */
    public Resilience4jConfigurationDefinition failureRateThreshold(Float failureRateThreshold) {
        setFailureRateThreshold(failureRateThreshold);
        return this;
    }

    /**
     * Configures the number of permitted calls when the CircuitBreaker is half open.
     * <p>
     * The size must be greater than 0. Default size is 10.
     */
    public Resilience4jConfigurationDefinition permittedNumberOfCallsInHalfOpenState(Integer permittedNumberOfCallsInHalfOpenState) {
        setPermittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState);
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
    public Resilience4jConfigurationDefinition slidingWindowSize(Integer slidingWindowSize) {
        setSlidingWindowSize(slidingWindowSize);
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
    public Resilience4jConfigurationDefinition slidingWindowType(String slidingWindowType) {
        setSlidingWindowType(slidingWindowType);
        return this;
    }

    /**
     * Configures configures the minimum number of calls which are required (per sliding window period) before the CircuitBreaker can calculate the error rate.
     * For example, if {@code minimumNumberOfCalls} is 10, then at least 10 calls must be recorded, before the failure rate can be calculated.
     * If only 9 calls have been recorded the CircuitBreaker will not transition to open even if all 9 calls have failed.
     *
     * Default minimumNumberOfCalls is 100
     */
    public Resilience4jConfigurationDefinition minimumNumberOfCalls(Integer minimumNumberOfCalls) {
        setMinimumNumberOfCalls(minimumNumberOfCalls);
        return this;
    }

    /**
     * Enables writable stack traces. When set to false, Exception.getStackTrace returns a zero length array.
     * This may be used to reduce log spam when the circuit breaker is open as the cause of the exceptions is already known (the circuit breaker is short-circuiting calls).
     */
    public Resilience4jConfigurationDefinition writableStackTraceEnabled(Boolean writableStackTraceEnabled) {
        setWritableStackTraceEnabled(writableStackTraceEnabled);
        return this;
    }

    /**
     * Configures the wait duration (in seconds) which specifies how long the CircuitBreaker should stay open, before it switches to half open.
     * Default value is 60 seconds.
     */
    public Resilience4jConfigurationDefinition waitDurationInOpenState(Integer waitDurationInOpenState) {
        setWaitDurationInOpenState(waitDurationInOpenState);
        return this;
    }

    /**
     * Enables automatic transition from OPEN to HALF_OPEN state once the waitDurationInOpenState has passed.
     */
    public Resilience4jConfigurationDefinition automaticTransitionFromOpenToHalfOpenEnabled(Boolean automaticTransitionFromOpenToHalfOpenEnabled) {
        setAutomaticTransitionFromOpenToHalfOpenEnabled(automaticTransitionFromOpenToHalfOpenEnabled);
        return this;
    }

    /**
     * Configures a threshold in percentage. The CircuitBreaker considers a call as slow when the call duration is greater than slowCallDurationThreshold(Duration.
     * When the percentage of slow calls is equal or greater the threshold, the CircuitBreaker transitions to open and starts short-circuiting calls.
     * <p>
     * The threshold must be greater than 0 and not greater than 100.
     * Default value is 100 percentage which means that all recorded calls must be slower than slowCallDurationThreshold.
     */
    public Resilience4jConfigurationDefinition slowCallRateThreshold(Float slowCallRateThreshold) {
        setSlowCallRateThreshold(slowCallRateThreshold);
        return this;
    }

    /**
     * Configures the duration threshold (seconds) above which calls are considered as slow and increase the slow calls percentage.
     * Default value is 60 seconds.
     */
    public Resilience4jConfigurationDefinition slowCallDurationThreshold(Integer slowCallDurationThreshold) {
        setSlowCallDurationThreshold(slowCallDurationThreshold);
        return this;
    }

    /**
     * End of configuration.
     */
    public CircuitBreakerDefinition end() {
        return parent;
    }

}
