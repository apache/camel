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

import java.util.concurrent.ExecutorService;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.Metadata;

/**
 * Controls the rate at which messages are passed to the next node in the route
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "throttle")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = { "expression", "correlationExpression" })
public class ThrottleDefinition extends ExpressionNode implements ExecutorServiceAwareDefinition<ThrottleDefinition> {

    @XmlTransient
    private ExecutorService executorServiceBean;

    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.model.ThrottlingMode", defaultValue = "TotalRequests",
              enums = "TotalRequests,ConcurrentRequests")
    private String mode;
    @XmlElement(name = "correlationExpression")
    private ExpressionSubElementDefinition correlationExpression;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.util.concurrent.ExecutorService")
    private String executorService;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String asyncDelayed;
    @XmlAttribute
    @Metadata(label = "advanced", defaultValue = "true", javaType = "java.lang.Boolean")
    private String callerRunsWhenRejected;
    @XmlAttribute
    @Metadata(label = "advanced", javaType = "java.lang.Boolean")
    private String rejectExecution;
    @XmlAttribute
    @Metadata(defaultValue = "1000", javaType = "java.time.Duration")
    private String timePeriodMillis;

    public ThrottleDefinition() {
        totalRequestsMode();
    }

    public ThrottleDefinition(Expression maximumRequestsPerPeriod) {
        super(maximumRequestsPerPeriod);

        totalRequestsMode();
    }

    public ThrottleDefinition(Expression maximumRequestsPerPeriod, Expression correlationExpression) {
        this(ExpressionNodeHelper.toExpressionDefinition(maximumRequestsPerPeriod), correlationExpression);
    }

    private ThrottleDefinition(ExpressionDefinition maximumRequestsPerPeriod, Expression correlationExpression) {
        super(maximumRequestsPerPeriod);

        ExpressionSubElementDefinition cor = new ExpressionSubElementDefinition();
        cor.setExpressionType(ExpressionNodeHelper.toExpressionDefinition(correlationExpression));
        setCorrelationExpression(cor);

        totalRequestsMode();
    }

    public ThrottleDefinition totalRequestsMode() {
        this.mode = ThrottlingMode.TotalRequests.name();
        return this;
    }

    public ThrottleDefinition concurrentRequestsMode() {
        this.mode = ThrottlingMode.ConcurrentRequests.name();
        return this;
    }

    @Override
    public String toString() {
        return "Throttle[" + description() + "]";
    }

    protected String description() {
        if (mode.equals(ThrottlingMode.TotalRequests.name())) {
            return getExpression() + " request per " + getTimePeriodMillis() + " millis";
        } else {
            return getExpression() + " maximum concurrent requests";
        }
    }

    @Override
    public String getShortName() {
        return "throttle";
    }

    @Override
    public String getLabel() {
        return "throttle[" + description() + "]";
    }

    // Fluent API
    // -------------------------------------------------------------------------
    /**
     * Sets the time period during which the maximum request count is valid for
     *
     * @param  timePeriodMillis period in millis
     * @return                  the builder
     */
    public ThrottleDefinition timePeriodMillis(long timePeriodMillis) {
        return timePeriodMillis(Long.toString(timePeriodMillis));
    }

    /**
     * Sets the time period during which the maximum request count is valid for
     *
     * @param  timePeriodMillis period in millis
     * @return                  the builder
     */
    public ThrottleDefinition timePeriodMillis(String timePeriodMillis) {
        setTimePeriodMillis(timePeriodMillis);
        return this;
    }

    /**
     * Sets the maximum number of requests
     *
     * @param  maximumConcurrentRequests the maximum number of requests (according to the mode in use - either
     *                                   concurrent or by time period)
     * @return                           the builder
     */
    public ThrottleDefinition maximumRequests(long maximumConcurrentRequests) {
        setExpression(
                ExpressionNodeHelper.toExpressionDefinition(ExpressionBuilder.constantExpression(maximumConcurrentRequests)));
        return this;
    }

    /**
     * Sets the maximum number of requests
     *
     * @param  maximumConcurrentRequests the maximum number of requests (according to the mode in use - either
     *                                   concurrent or by time period)
     * @return                           the builder
     */
    public ThrottleDefinition maximumRequests(String maximumConcurrentRequests) {
        setExpression(
                ExpressionNodeHelper.toExpressionDefinition(ExpressionBuilder.constantExpression(maximumConcurrentRequests)));
        return this;
    }

    /**
     * Sets the time period during which the maximum request count per period
     *
     * @param      maximumRequestsPerPeriod the maximum request count number per time period
     * @deprecated                          Use {@link #maximumRequests(long)}
     * @return                              the builder
     */
    @Deprecated(since = "4.4.0")
    public ThrottleDefinition maximumRequestsPerPeriod(long maximumRequestsPerPeriod) {
        if (ThrottlingMode.toMode(mode) == ThrottlingMode.TotalRequests) {
            setExpression(
                    ExpressionNodeHelper.toExpressionDefinition(
                            ExpressionBuilder.constantExpression(maximumRequestsPerPeriod)));
            return this;
        } else {
            throw new IllegalArgumentException("Maximum requests per period can only be set when using total requests mode");
        }
    }

    /**
     * Sets the time period during which the maximum request count per period
     *
     * @param      maximumRequestsPerPeriod the maximum request count number per time period
     * @deprecated                          Use {@link #maximumRequests(long)}
     * @return                              the builder
     */
    @Deprecated(since = "4.4.0")
    public ThrottleDefinition maximumRequestsPerPeriod(String maximumRequestsPerPeriod) {
        if (ThrottlingMode.toMode(mode) == ThrottlingMode.TotalRequests) {
            setExpression(
                    ExpressionNodeHelper.toExpressionDefinition(ExpressionBuilder.simpleExpression(maximumRequestsPerPeriod)));
            return this;
        } else {
            throw new IllegalArgumentException("Maximum requests per period can only be set when using total requests mode");
        }
    }

    /**
     * To use a correlation expression that can throttle by the given key instead of overall throttling
     *
     * @param  correlationExpression is a correlation key as a long number that can throttle by the given key instead of
     *                               overall throttling
     * @return                       the builder
     */
    public ThrottleDefinition correlationExpression(long correlationExpression) {
        return correlationExpression(ExpressionBuilder.constantExpression(correlationExpression));
    }

    /**
     * To use a correlation expression that can throttle by the given key instead of overall throttling
     *
     * @param  correlationExpression is a correlation key as an expression that can throttle by the given key instead of
     *                               overall throttling
     * @return                       the builder
     */
    public ThrottleDefinition correlationExpression(Expression correlationExpression) {
        ExpressionSubElementDefinition cor = new ExpressionSubElementDefinition();
        cor.setExpressionType(ExpressionNodeHelper.toExpressionDefinition(correlationExpression));
        setCorrelationExpression(cor);
        return this;
    }

    /**
     * Whether or not the caller should run the task when it was rejected by the thread pool.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param  callerRunsWhenRejected whether or not the caller should run
     * @return                        the builder
     */
    public ThrottleDefinition callerRunsWhenRejected(boolean callerRunsWhenRejected) {
        return callerRunsWhenRejected(Boolean.toString(callerRunsWhenRejected));
    }

    /**
     * Whether or not the caller should run the task when it was rejected by the thread pool.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param  callerRunsWhenRejected whether or not the caller should run
     * @return                        the builder
     */
    public ThrottleDefinition callerRunsWhenRejected(String callerRunsWhenRejected) {
        setCallerRunsWhenRejected(callerRunsWhenRejected);
        return this;
    }

    /**
     * Enables asynchronous delay which means the thread will <b>not</b> block while delaying.
     *
     * @return the builder
     */
    public ThrottleDefinition asyncDelayed() {
        return asyncDelayed(true);
    }

    /**
     * Enables asynchronous delay which means the thread will <b>not</b> block while delaying.
     *
     * @return the builder
     */
    public ThrottleDefinition asyncDelayed(boolean asyncDelayed) {
        return asyncDelayed(Boolean.toString(asyncDelayed));
    }

    /**
     * Enables asynchronous delay which means the thread will <b>not</b> block while delaying.
     *
     * @return the builder
     */
    public ThrottleDefinition asyncDelayed(String asyncDelayed) {
        setAsyncDelayed(asyncDelayed);
        return this;
    }

    /**
     * Whether or not throttler throws the ThrottlerRejectedExecutionException when the exchange exceeds the request
     * limit
     * <p/>
     * Is by default <tt>false</tt>
     *
     * @param  rejectExecution throw the RejectExecutionException if the exchange exceeds the request limit
     * @return                 the builder
     */
    public ThrottleDefinition rejectExecution(boolean rejectExecution) {
        return rejectExecution(Boolean.toString(rejectExecution));
    }

    /**
     * Whether or not throttler throws the ThrottlerRejectedExecutionException when the exchange exceeds the request
     * limit
     * <p/>
     * Is by default <tt>false</tt>
     *
     * @param  rejectExecution throw the RejectExecutionException if the exchange exceeds the request limit
     * @return                 the builder
     */
    public ThrottleDefinition rejectExecution(String rejectExecution) {
        setRejectExecution(rejectExecution);
        return this;
    }

    /**
     * To use a custom thread pool (ScheduledExecutorService) by the throttler.
     *
     * @param  executorService the custom thread pool (must be scheduled)
     * @return                 the builder
     */
    @Override
    public ThrottleDefinition executorService(ExecutorService executorService) {
        this.executorServiceBean = executorService;
        return this;
    }

    /**
     * To use a custom thread pool (ScheduledExecutorService) by the throttler.
     *
     * @param  executorService the reference id of the thread pool (must be scheduled)
     * @return                 the builder
     */
    @Override
    public ThrottleDefinition executorService(String executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Sets the throttling mode to one of the available modes enumerated in ThrottlingMode
     *
     * @param  mode The throttling mode as a string parameter. It currently accepts one of 'TotalRequests' or
     *              `ConcurrentRequests`
     * @see         ThrottlingMode
     * @return      the builder
     */
    public ThrottleDefinition mode(String mode) {
        setMode(mode);

        return this;
    }

    // Properties
    // -------------------------------------------------------------------------

    @Override
    public ExecutorService getExecutorServiceBean() {
        return executorServiceBean;
    }

    @Override
    public String getExecutorServiceRef() {
        return executorService;
    }

    /**
     * Expression to configure the maximum number of messages to throttle per request
     */
    @Override
    public void setExpression(ExpressionDefinition expression) {
        // override to include javadoc what the expression is used for
        super.setExpression(expression);
    }

    public String getTimePeriodMillis() {
        if (ThrottlingMode.toMode(mode) == ThrottlingMode.TotalRequests) {
            return timePeriodMillis;
        }

        throw new IllegalArgumentException("Time period in millis can only be obtained when using total requests mode");
    }

    public void setTimePeriodMillis(String timePeriodMillis) {
        if (ThrottlingMode.toMode(mode) == ThrottlingMode.TotalRequests) {
            this.timePeriodMillis = timePeriodMillis;
        } else {
            throw new IllegalArgumentException("Time period in millis can only be set when using total requests mode");
        }
    }

    public String getAsyncDelayed() {
        return asyncDelayed;
    }

    public void setAsyncDelayed(String asyncDelayed) {
        this.asyncDelayed = asyncDelayed;
    }

    public String getCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(String callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    public String getRejectExecution() {
        return rejectExecution;
    }

    public void setRejectExecution(String rejectExecution) {
        this.rejectExecution = rejectExecution;
    }

    /**
     * The expression used to calculate the correlation key to use for throttle grouping. The Exchange which has the
     * same correlation key is throttled together.
     */
    public void setCorrelationExpression(ExpressionSubElementDefinition correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    public ExpressionSubElementDefinition getCorrelationExpression() {
        return correlationExpression;
    }

    public String getExecutorService() {
        return executorService;
    }

    public void setExecutorService(String executorService) {
        this.executorService = executorService;
    }

    public String getMode() {
        return mode;
    }

    /**
     * Sets the throttling mode to one of the available modes enumerated in ThrottlingMode
     *
     * @param mode The throttling mode as a string parameter. It currently accepts one of 'TotalRequests' or
     *             `ConcurrentRequests`
     * @see        ThrottlingMode
     */
    public void setMode(String mode) {
        this.mode = mode;
    }
}
