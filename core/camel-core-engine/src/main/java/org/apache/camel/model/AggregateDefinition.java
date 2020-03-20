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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionFactory;
import org.apache.camel.Predicate;
import org.apache.camel.builder.AggregationStrategyClause;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.builder.PredicateClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.Metadata;

/**
 * Aggregates many messages into a single message
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "aggregate")
@XmlAccessorType(XmlAccessType.FIELD)
public class AggregateDefinition extends OutputDefinition<AggregateDefinition> implements ExecutorServiceAwareDefinition<AggregateDefinition> {
    @XmlElement(name = "correlationExpression", required = true)
    private ExpressionSubElementDefinition correlationExpression;
    @XmlElement(name = "completionPredicate")
    @AsPredicate
    private ExpressionSubElementDefinition completionPredicate;
    @XmlElement(name = "completionTimeoutExpression")
    private ExpressionSubElementDefinition completionTimeoutExpression;
    @XmlElement(name = "completionSizeExpression")
    private ExpressionSubElementDefinition completionSizeExpression;
    @XmlElement(name = "optimisticLockRetryPolicy")
    private OptimisticLockRetryPolicyDefinition optimisticLockRetryPolicyDefinition;
    @XmlTransient
    private ExpressionDefinition expression;
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
    @XmlTransient
    private ScheduledExecutorService timeoutCheckerExecutorService;
    @XmlTransient
    private AggregationRepository aggregationRepository;
    @XmlTransient
    private OptimisticLockRetryPolicy optimisticLockRetryPolicy;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String parallelProcessing;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String optimisticLocking;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private String timeoutCheckerExecutorServiceRef;
    @XmlAttribute
    private String aggregationRepositoryRef;
    @XmlAttribute
    private String strategyRef;
    @XmlAttribute
    private String strategyMethodName;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String strategyMethodAllowNull;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String completionSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String completionInterval;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String completionTimeout;
    @XmlAttribute
    @Metadata(defaultValue = "1000", javaType = "java.lang.Long")
    private String completionTimeoutCheckerInterval = Long.toString(1000L);
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String completionFromBatchConsumer;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String completionOnNewCorrelationGroup;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String eagerCheckCompletion;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String ignoreInvalidCorrelationKeys;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String closeCorrelationKeyOnCompletion;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String discardOnCompletionTimeout;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String discardOnAggregationFailure;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String forceCompletionOnStop;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String completeAllOnStop;
    @XmlTransient
    private AggregateController aggregateController;
    @XmlAttribute
    private String aggregateControllerRef;

    public AggregateDefinition() {
    }

    public AggregateDefinition(@AsPredicate Predicate predicate) {
        this(ExpressionNodeHelper.toExpressionDefinition(predicate));
    }

    public AggregateDefinition(Expression expression) {
        this(ExpressionNodeHelper.toExpressionDefinition(expression));
    }

    public AggregateDefinition(ExpressionDefinition correlationExpression) {
        setExpression(correlationExpression);

        ExpressionSubElementDefinition cor = new ExpressionSubElementDefinition();
        cor.setExpressionType(correlationExpression);
        setCorrelationExpression(cor);
    }

    public AggregateDefinition(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        this(correlationExpression);
        this.aggregationStrategy = aggregationStrategy;
    }

    @Override
    public String toString() {
        return "Aggregate[" + description() + " -> " + getOutputs() + "]";
    }

    protected String description() {
        return getExpression() != null ? getExpression().getLabel() : "";
    }

    @Override
    public String getShortName() {
        return "aggregate";
    }

    @Override
    public String getLabel() {
        return "aggregate[" + description() + "]";
    }

    @Override
    public void configureChild(ProcessorDefinition<?> output) {
        Expression exp = getExpression();
        if (getExpression() != null && getExpression().getExpressionValue() != null) {
            exp = getExpression().getExpressionValue();
        }

        if (exp instanceof ExpressionClause) {
            ExpressionClause<?> clause = (ExpressionClause<?>)exp;
            if (clause.getExpressionType() != null) {
                // if using the Java DSL then the expression may have been set
                // using the
                // ExpressionClause which is a fancy builder to define
                // expressions and predicates
                // using fluent builders in the DSL. However we need afterwards
                // a callback to
                // reset the expression to the expression type the
                // ExpressionClause did build for us
                ExpressionFactory model = clause.getExpressionType();
                if (model instanceof ExpressionDefinition) {
                    correlationExpression = new ExpressionSubElementDefinition();
                    correlationExpression.setExpressionType((ExpressionDefinition)model);
                }
            }
        }
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * The AggregationStrategy to use.
     * <p/>
     * Configuring an AggregationStrategy is required, and is used to merge the
     * incoming Exchange with the existing already merged exchanges. At first
     * call the oldExchange parameter is null. On subsequent invocations the
     * oldExchange contains the merged exchanges and newExchange is of course
     * the new incoming Exchange.
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public String getAggregationStrategyRef() {
        return strategyRef;
    }

    /**
     * A reference to lookup the AggregationStrategy in the Registry.
     * <p/>
     * Configuring an AggregationStrategy is required, and is used to merge the
     * incoming Exchange with the existing already merged exchanges. At first
     * call the oldExchange parameter is null. On subsequent invocations the
     * oldExchange contains the merged exchanges and newExchange is of course
     * the new incoming Exchange.
     */
    public void setAggregationStrategyRef(String aggregationStrategyRef) {
        this.strategyRef = aggregationStrategyRef;
    }

    public String getStrategyRef() {
        return strategyRef;
    }

    /**
     * A reference to lookup the AggregationStrategy in the Registry.
     * <p/>
     * Configuring an AggregationStrategy is required, and is used to merge the
     * incoming Exchange with the existing already merged exchanges. At first
     * call the oldExchange parameter is null. On subsequent invocations the
     * oldExchange contains the merged exchanges and newExchange is of course
     * the new incoming Exchange.
     */
    public void setStrategyRef(String strategyRef) {
        this.strategyRef = strategyRef;
    }

    public String getAggregationStrategyMethodName() {
        return strategyMethodName;
    }

    /**
     * This option can be used to explicit declare the method name to use, when
     * using POJOs as the AggregationStrategy.
     */
    public void setAggregationStrategyMethodName(String strategyMethodName) {
        this.strategyMethodName = strategyMethodName;
    }

    public String getStrategyMethodAllowNull() {
        return strategyMethodAllowNull;
    }

    public String getStrategyMethodName() {
        return strategyMethodName;
    }

    /**
     * This option can be used to explicit declare the method name to use, when
     * using POJOs as the AggregationStrategy.
     */
    public void setStrategyMethodName(String strategyMethodName) {
        this.strategyMethodName = strategyMethodName;
    }

    /**
     * If this option is false then the aggregate method is not used for the
     * very first aggregation. If this option is true then null values is used
     * as the oldExchange (at the very first aggregation), when using POJOs as
     * the AggregationStrategy.
     */
    public void setStrategyMethodAllowNull(String strategyMethodAllowNull) {
        this.strategyMethodAllowNull = strategyMethodAllowNull;
    }

    /**
     * The expression used to calculate the correlation key to use for
     * aggregation. The Exchange which has the same correlation key is
     * aggregated together. If the correlation key could not be evaluated an
     * Exception is thrown. You can disable this by using the
     * ignoreBadCorrelationKeys option.
     */
    public void setCorrelationExpression(ExpressionSubElementDefinition correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    public ExpressionSubElementDefinition getCorrelationExpression() {
        return correlationExpression;
    }

    public String getCompletionSize() {
        return completionSize;
    }

    public void setCompletionSize(String completionSize) {
        this.completionSize = completionSize;
    }

    public OptimisticLockRetryPolicyDefinition getOptimisticLockRetryPolicyDefinition() {
        return optimisticLockRetryPolicyDefinition;
    }

    public void setOptimisticLockRetryPolicyDefinition(OptimisticLockRetryPolicyDefinition optimisticLockRetryPolicyDefinition) {
        this.optimisticLockRetryPolicyDefinition = optimisticLockRetryPolicyDefinition;
    }

    public OptimisticLockRetryPolicy getOptimisticLockRetryPolicy() {
        return optimisticLockRetryPolicy;
    }

    public void setOptimisticLockRetryPolicy(OptimisticLockRetryPolicy optimisticLockRetryPolicy) {
        this.optimisticLockRetryPolicy = optimisticLockRetryPolicy;
    }

    public String getCompletionInterval() {
        return completionInterval;
    }

    public void setCompletionInterval(String completionInterval) {
        this.completionInterval = completionInterval;
    }

    public String getCompletionTimeout() {
        return completionTimeout;
    }

    public void setCompletionTimeout(String completionTimeout) {
        this.completionTimeout = completionTimeout;
    }

    public String getCompletionTimeoutCheckerInterval() {
        return completionTimeoutCheckerInterval;
    }

    public void setCompletionTimeoutCheckerInterval(String completionTimeoutCheckerInterval) {
        this.completionTimeoutCheckerInterval = completionTimeoutCheckerInterval;
    }

    public ExpressionSubElementDefinition getCompletionPredicate() {
        return completionPredicate;
    }

    public void setCompletionPredicate(ExpressionSubElementDefinition completionPredicate) {
        this.completionPredicate = completionPredicate;
    }

    public ExpressionSubElementDefinition getCompletionTimeoutExpression() {
        return completionTimeoutExpression;
    }

    /**
     * Time in millis that an aggregated exchange should be inactive before its
     * complete (timeout). This option can be set as either a fixed value or
     * using an Expression which allows you to evaluate a timeout dynamically -
     * will use Long as result. If both are set Camel will fallback to use the
     * fixed value if the Expression result was null or 0. You cannot use this
     * option together with completionInterval, only one of the two can be used.
     * <p/>
     * By default the timeout checker runs every second, you can use the
     * completionTimeoutCheckerInterval option to configure how frequently to
     * run the checker. The timeout is an approximation and there is no
     * guarantee that the a timeout is triggered exactly after the timeout
     * value. It is not recommended to use very low timeout values or checker
     * intervals.
     *
     * @param completionTimeoutExpression the timeout as an {@link Expression}
     *            which is evaluated as a {@link Long} type
     */
    public void setCompletionTimeoutExpression(ExpressionSubElementDefinition completionTimeoutExpression) {
        this.completionTimeoutExpression = completionTimeoutExpression;
    }

    public ExpressionSubElementDefinition getCompletionSizeExpression() {
        return completionSizeExpression;
    }

    /**
     * Number of messages aggregated before the aggregation is complete. This
     * option can be set as either a fixed value or using an Expression which
     * allows you to evaluate a size dynamically - will use Integer as result.
     * If both are set Camel will fallback to use the fixed value if the
     * Expression result was null or 0.
     *
     * @param completionSizeExpression the completion size as an
     *            {@link org.apache.camel.Expression} which is evaluated as a
     *            {@link Integer} type
     */
    public void setCompletionSizeExpression(ExpressionSubElementDefinition completionSizeExpression) {
        this.completionSizeExpression = completionSizeExpression;
    }

    public String getCompletionFromBatchConsumer() {
        return completionFromBatchConsumer;
    }

    public void setCompletionFromBatchConsumer(String completionFromBatchConsumer) {
        this.completionFromBatchConsumer = completionFromBatchConsumer;
    }

    public String getCompletionOnNewCorrelationGroup() {
        return completionOnNewCorrelationGroup;
    }

    public void setCompletionOnNewCorrelationGroup(String completionOnNewCorrelationGroup) {
        this.completionOnNewCorrelationGroup = completionOnNewCorrelationGroup;
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getOptimisticLocking() {
        return optimisticLocking;
    }

    public void setOptimisticLocking(String optimisticLocking) {
        this.optimisticLocking = optimisticLocking;
    }

    public String getParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(String parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    @Override
    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    @Override
    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public String getEagerCheckCompletion() {
        return eagerCheckCompletion;
    }

    public void setEagerCheckCompletion(String eagerCheckCompletion) {
        this.eagerCheckCompletion = eagerCheckCompletion;
    }

    public String getIgnoreInvalidCorrelationKeys() {
        return ignoreInvalidCorrelationKeys;
    }

    public void setIgnoreInvalidCorrelationKeys(String ignoreInvalidCorrelationKeys) {
        this.ignoreInvalidCorrelationKeys = ignoreInvalidCorrelationKeys;
    }

    public String getCloseCorrelationKeyOnCompletion() {
        return closeCorrelationKeyOnCompletion;
    }

    public void setCloseCorrelationKeyOnCompletion(String closeCorrelationKeyOnCompletion) {
        this.closeCorrelationKeyOnCompletion = closeCorrelationKeyOnCompletion;
    }

    public AggregationRepository getAggregationRepository() {
        return aggregationRepository;
    }

    public void setAggregationRepository(AggregationRepository aggregationRepository) {
        this.aggregationRepository = aggregationRepository;
    }

    public String getAggregationRepositoryRef() {
        return aggregationRepositoryRef;
    }

    public void setAggregationRepositoryRef(String aggregationRepositoryRef) {
        this.aggregationRepositoryRef = aggregationRepositoryRef;
    }

    public String getDiscardOnCompletionTimeout() {
        return discardOnCompletionTimeout;
    }

    public void setDiscardOnCompletionTimeout(String discardOnCompletionTimeout) {
        this.discardOnCompletionTimeout = discardOnCompletionTimeout;
    }

    public String getDiscardOnAggregationFailure() {
        return discardOnAggregationFailure;
    }

    public void setDiscardOnAggregationFailure(String discardOnAggregationFailure) {
        this.discardOnAggregationFailure = discardOnAggregationFailure;
    }

    public void setTimeoutCheckerExecutorService(ScheduledExecutorService timeoutCheckerExecutorService) {
        this.timeoutCheckerExecutorService = timeoutCheckerExecutorService;
    }

    public ScheduledExecutorService getTimeoutCheckerExecutorService() {
        return timeoutCheckerExecutorService;
    }

    public void setTimeoutCheckerExecutorServiceRef(String timeoutCheckerExecutorServiceRef) {
        this.timeoutCheckerExecutorServiceRef = timeoutCheckerExecutorServiceRef;
    }

    public String getTimeoutCheckerExecutorServiceRef() {
        return timeoutCheckerExecutorServiceRef;
    }

    public String getForceCompletionOnStop() {
        return forceCompletionOnStop;
    }

    public void setForceCompletionOnStop(String forceCompletionOnStop) {
        this.forceCompletionOnStop = forceCompletionOnStop;
    }

    public String getCompleteAllOnStop() {
        return completeAllOnStop;
    }

    public void setCompleteAllOnStop(String completeAllOnStop) {
        this.completeAllOnStop = completeAllOnStop;
    }

    public AggregateController getAggregateController() {
        return aggregateController;
    }

    public void setAggregateController(AggregateController aggregateController) {
        this.aggregateController = aggregateController;
    }

    public String getAggregateControllerRef() {
        return aggregateControllerRef;
    }

    /**
     * To use a {@link org.apache.camel.processor.aggregate.AggregateController}
     * to allow external sources to control this aggregator.
     */
    public void setAggregateControllerRef(String aggregateControllerRef) {
        this.aggregateControllerRef = aggregateControllerRef;
    }

    // Fluent API
    // -------------------------------------------------------------------------

    /**
     * Use eager completion checking which means that the completionPredicate
     * will use the incoming Exchange. As opposed to without eager completion
     * checking the completionPredicate will use the aggregated Exchange.
     *
     * @return builder
     */
    public AggregateDefinition eagerCheckCompletion() {
        setEagerCheckCompletion(Boolean.toString(true));
        return this;
    }

    /**
     * If a correlation key cannot be successfully evaluated it will be ignored
     * by logging a DEBUG and then just ignore the incoming Exchange.
     *
     * @return builder
     */
    public AggregateDefinition ignoreInvalidCorrelationKeys() {
        setIgnoreInvalidCorrelationKeys(Boolean.toString(true));
        return this;
    }

    /**
     * Closes a correlation key when its complete. Any <i>late</i> received
     * exchanges which has a correlation key that has been closed, it will be
     * defined and a ClosedCorrelationKeyException is thrown.
     *
     * @param capacity the maximum capacity of the closed correlation key cache.
     *            Use <tt>0</tt> or negative value for unbounded capacity.
     * @return builder
     */
    public AggregateDefinition closeCorrelationKeyOnCompletion(int capacity) {
        setCloseCorrelationKeyOnCompletion(Integer.toString(capacity));
        return this;
    }

    /**
     * Discards the aggregated message on completion timeout.
     * <p/>
     * This means on timeout the aggregated message is dropped and not sent out
     * of the aggregator.
     *
     * @return builder
     */
    public AggregateDefinition discardOnCompletionTimeout() {
        setDiscardOnCompletionTimeout(Boolean.toString(true));
        return this;
    }

    /**
     * Discards the aggregated message when aggregation failed (an exception was
     * thrown from {@link AggregationStrategy}. This means the partly aggregated
     * message is dropped and not sent out of the aggregator.
     * <p/>
     * This option cannot be used together with completionFromBatchConsumer.
     *
     * @return builder
     */
    public AggregateDefinition discardOnAggregationFailure() {
        setDiscardOnAggregationFailure(Boolean.toString(true));
        return this;
    }

    /**
     * Enables the batch completion mode where we aggregate from a
     * {@link org.apache.camel.BatchConsumer} and aggregate the total number of
     * exchanges the {@link org.apache.camel.BatchConsumer} has reported as
     * total by checking the exchange property
     * {@link org.apache.camel.Exchange#BATCH_COMPLETE} when its complete.
     * <p/>
     * This option cannot be used together with discardOnAggregationFailure.
     *
     * @return builder
     */
    public AggregateDefinition completionFromBatchConsumer() {
        setCompletionFromBatchConsumer(Boolean.toString(true));
        return this;
    }

    /**
     * Enables completion on all previous groups when a new incoming correlation
     * group. This can for example be used to complete groups with same
     * correlation keys when they are in consecutive order. Notice when this is
     * enabled then only 1 correlation group can be in progress as when a new
     * correlation group starts, then the previous groups is forced completed.
     *
     * @return builder
     */
    public AggregateDefinition completionOnNewCorrelationGroup() {
        setCompletionOnNewCorrelationGroup(Boolean.toString(true));
        return this;
    }

    /**
     * Number of messages aggregated before the aggregation is complete. This
     * option can be set as either a fixed value or using an Expression which
     * allows you to evaluate a size dynamically - will use Integer as result.
     * If both are set Camel will fallback to use the fixed value if the
     * Expression result was null or 0.
     *
     * @param completionSize the completion size, must be a an expression evaluating to positive number
     * @return builder
     */
    public AggregateDefinition completionSize(String completionSize) {
        setCompletionSize(completionSize);
        return this;
    }

    /**
     * Number of messages aggregated before the aggregation is complete. This
     * option can be set as either a fixed value or using an Expression which
     * allows you to evaluate a size dynamically - will use Integer as result.
     * If both are set Camel will fallback to use the fixed value if the
     * Expression result was null or 0.
     *
     * @param completionSize the completion size, must be a positive number
     * @return builder
     */
    public AggregateDefinition completionSize(int completionSize) {
        setCompletionSize(Integer.toString(completionSize));
        return this;
    }

    /**
     * Number of messages aggregated before the aggregation is complete. This
     * option can be set as either a fixed value or using an Expression which
     * allows you to evaluate a size dynamically - will use Integer as result.
     * If both are set Camel will fallback to use the fixed value if the
     * Expression result was null or 0.
     *
     * @param completionSize the completion size as an
     *            {@link org.apache.camel.Expression} which is evaluated as a
     *            {@link Integer} type
     * @return builder
     */
    public AggregateDefinition completionSize(Expression completionSize) {
        setCompletionSizeExpression(new ExpressionSubElementDefinition(completionSize));
        return this;
    }

    /**
     * A repeating period in millis by which the aggregator will complete all
     * current aggregated exchanges. Camel has a background task which is
     * triggered every period. You cannot use this option together with
     * completionTimeout, only one of them can be used.
     *
     * @param completionInterval the interval in millis, must be a positive
     *            value
     * @return the builder
     */
    public AggregateDefinition completionInterval(long completionInterval) {
        setCompletionInterval(Long.toString(completionInterval));
        return this;
    }

    /**
     * Time in millis that an aggregated exchange should be inactive before its
     * complete (timeout). This option can be set as either a fixed value or
     * using an Expression which allows you to evaluate a timeout dynamically -
     * will use Long as result. If both are set Camel will fallback to use the
     * fixed value if the Expression result was null or 0. You cannot use this
     * option together with completionInterval, only one of the two can be used.
     * <p/>
     * By default the timeout checker runs every second, you can use the
     * completionTimeoutCheckerInterval option to configure how frequently to
     * run the checker. The timeout is an approximation and there is no
     * guarantee that the a timeout is triggered exactly after the timeout
     * value. It is not recommended to use very low timeout values or checker
     * intervals.
     *
     * @param completionTimeout the timeout in millis, must be a positive value
     * @return the builder
     */
    public AggregateDefinition completionTimeout(long completionTimeout) {
        setCompletionTimeout(Long.toString(completionTimeout));
        return this;
    }

    /**
     * Time in millis that an aggregated exchange should be inactive before its
     * complete (timeout). This option can be set as either a fixed value or
     * using an Expression which allows you to evaluate a timeout dynamically -
     * will use Long as result. If both are set Camel will fallback to use the
     * fixed value if the Expression result was null or 0. You cannot use this
     * option together with completionInterval, only one of the two can be used.
     * <p/>
     * By default the timeout checker runs every second, you can use the
     * completionTimeoutCheckerInterval option to configure how frequently to
     * run the checker. The timeout is an approximation and there is no
     * guarantee that the a timeout is triggered exactly after the timeout
     * value. It is not recommended to use very low timeout values or checker
     * intervals.
     *
     * @param completionTimeout the timeout as an {@link Expression} which is
     *            evaluated as a {@link Long} type
     * @return the builder
     */
    public AggregateDefinition completionTimeout(Expression completionTimeout) {
        setCompletionTimeoutExpression(new ExpressionSubElementDefinition(completionTimeout));
        return this;
    }

    /**
     * Interval in millis that is used by the background task that checks for
     * timeouts ({@link org.apache.camel.TimeoutMap}).
     * <p/>
     * By default the timeout checker runs every second. The timeout is an
     * approximation and there is no guarantee that the a timeout is triggered
     * exactly after the timeout value. It is not recommended to use very low
     * timeout values or checker intervals.
     *
     * @param completionTimeoutCheckerInterval the interval in millis, must be a
     *            positive value
     * @return the builder
     */
    public AggregateDefinition completionTimeoutCheckerInterval(long completionTimeoutCheckerInterval) {
        setCompletionTimeoutCheckerInterval(Long.toString(completionTimeoutCheckerInterval));
        return this;
    }

    /**
     * Sets the AggregationStrategy to use with a fluent builder.
     */
    public AggregationStrategyClause<AggregateDefinition> aggregationStrategy() {
        AggregationStrategyClause<AggregateDefinition> clause = new AggregationStrategyClause<>(this);
        setAggregationStrategy(clause);
        return clause;
    }

    /**
     * Sets the AggregationStrategy to use with a fluent builder.
     *
     * @deprecated use {@link #aggregationStrategy()}
     */
    @Deprecated
    public AggregationStrategyClause<AggregateDefinition> strategy() {
        return aggregationStrategy();
    }

    /**
     * Sets the aggregate strategy to use
     *
     * @param aggregationStrategy the aggregate strategy to use
     * @return the builder
     * @deprecated use {@link #aggregationStrategy(AggregationStrategy)}
     */
    @Deprecated
    public AggregateDefinition strategy(AggregationStrategy aggregationStrategy) {
        return aggregationStrategy(aggregationStrategy);
    }

    /**
     * Sets the aggregate strategy to use
     *
     * @param aggregationStrategy the aggregate strategy to use
     * @return the builder
     */
    public AggregateDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Sets the aggregate strategy to use
     *
     * @param aggregationStrategy the aggregate strategy to use
     * @return the builder
     */
    public AggregateDefinition aggregationStrategy(Supplier<AggregationStrategy> aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy.get());
        return this;
    }

    /**
     * Sets the aggregate strategy to use
     *
     * @param aggregationStrategyRef reference to the strategy to lookup in the
     *            registry
     * @return the builder
     */
    public AggregateDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setAggregationStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * Sets the method name to use when using a POJO as
     * {@link AggregationStrategy}.
     *
     * @param methodName the method name to call
     * @return the builder
     */
    public AggregateDefinition aggregationStrategyMethodName(String methodName) {
        setAggregationStrategyMethodName(methodName);
        return this;
    }

    /**
     * Sets allowing null when using a POJO as {@link AggregationStrategy}.
     *
     * @return the builder
     */
    public AggregateDefinition aggregationStrategyMethodAllowNull() {
        setStrategyMethodAllowNull(Boolean.toString(true));
        return this;
    }

    /**
     * Sets the custom aggregate repository to use.
     * <p/>
     * Will by default use
     * {@link org.apache.camel.processor.aggregate.MemoryAggregationRepository}
     *
     * @param aggregationRepository the aggregate repository to use
     * @return the builder
     */
    public AggregateDefinition aggregationRepository(AggregationRepository aggregationRepository) {
        setAggregationRepository(aggregationRepository);
        return this;
    }

    /**
     * Sets the custom aggregate repository to use.
     * <p/>
     * Will by default use
     * {@link org.apache.camel.processor.aggregate.MemoryAggregationRepository}
     *
     * @param aggregationRepository the aggregate repository to use
     * @return the builder
     */
    public AggregateDefinition aggregationRepository(Supplier<AggregationRepository> aggregationRepository) {
        setAggregationRepository(aggregationRepository.get());
        return this;
    }

    /**
     * Sets the custom aggregate repository to use.
     * <p/>
     * Will by default use
     * {@link org.apache.camel.processor.aggregate.MemoryAggregationRepository}
     *
     * @param aggregationRepositoryRef reference to the repository to lookup in
     *            the registry
     * @return the builder
     */
    public AggregateDefinition aggregationRepositoryRef(String aggregationRepositoryRef) {
        setAggregationRepositoryRef(aggregationRepositoryRef);
        return this;
    }

    /**
     * A Predicate to indicate when an aggregated exchange is complete. If this
     * is not specified and the AggregationStrategy object implements Predicate,
     * the aggregationStrategy object will be used as the completionPredicate.
     */
    public AggregateDefinition completionPredicate(@AsPredicate Predicate predicate) {
        checkNoCompletedPredicate();
        setCompletionPredicate(new ExpressionSubElementDefinition(predicate));
        return this;
    }

    /**
     * A Predicate to indicate when an aggregated exchange is complete. If this
     * is not specified and the AggregationStrategy object implements Predicate,
     * the aggregationStrategy object will be used as the completionPredicate.
     */
    @AsPredicate
    public PredicateClause<AggregateDefinition> completionPredicate() {
        PredicateClause<AggregateDefinition> clause = new PredicateClause<>(this);
        completionPredicate(clause);
        return clause;
    }

    /**
     * A Predicate to indicate when an aggregated exchange is complete. If this
     * is not specified and the AggregationStrategy object implements Predicate,
     * the aggregationStrategy object will be used as the completionPredicate.
     */
    @AsPredicate
    public PredicateClause<AggregateDefinition> completion() {
        return completionPredicate();
    }

    /**
     * A Predicate to indicate when an aggregated exchange is complete. If this
     * is not specified and the AggregationStrategy object implements Predicate,
     * the aggregationStrategy object will be used as the completionPredicate.
     */
    public AggregateDefinition completion(@AsPredicate Predicate predicate) {
        return completionPredicate(predicate);
    }

    /**
     * Indicates to complete all current aggregated exchanges when the context
     * is stopped
     */
    public AggregateDefinition forceCompletionOnStop() {
        setForceCompletionOnStop(Boolean.toString(true));
        return this;
    }

    /**
     * Indicates to wait to complete all current and partial (pending)
     * aggregated exchanges when the context is stopped.
     * <p/>
     * This also means that we will wait for all pending exchanges which are
     * stored in the aggregation repository to complete so the repository is
     * empty before we can stop.
     * <p/>
     * You may want to enable this when using the memory based aggregation
     * repository that is memory based only, and do not store data on disk. When
     * this option is enabled, then the aggregator is waiting to complete all
     * those exchanges before its stopped, when stopping CamelContext or the
     * route using it.
     */
    public AggregateDefinition completeAllOnStop() {
        setCompleteAllOnStop(Boolean.toString(true));
        return this;
    }

    /**
     * When aggregated are completed they are being send out of the aggregator.
     * This option indicates whether or not Camel should use a thread pool with
     * multiple threads for concurrency. If no custom thread pool has been
     * specified then Camel creates a default pool with 10 concurrent threads.
     */
    public AggregateDefinition parallelProcessing() {
        setParallelProcessing(Boolean.toString(true));
        return this;
    }

    /**
     * When aggregated are completed they are being send out of the aggregator.
     * This option indicates whether or not Camel should use a thread pool with
     * multiple threads for concurrency. If no custom thread pool has been
     * specified then Camel creates a default pool with 10 concurrent threads.
     */
    public AggregateDefinition parallelProcessing(boolean parallelProcessing) {
        setParallelProcessing(Boolean.toString(parallelProcessing));
        return this;
    }

    /**
     * Turns on using optimistic locking, which requires the
     * aggregationRepository being used, is supporting this by implementing
     * {@link org.apache.camel.spi.OptimisticLockingAggregationRepository}.
     */
    public AggregateDefinition optimisticLocking() {
        setOptimisticLocking(Boolean.toString(true));
        return this;
    }

    /**
     * Allows to configure retry settings when using optimistic locking.
     */
    public AggregateDefinition optimisticLockRetryPolicy(OptimisticLockRetryPolicy policy) {
        setOptimisticLockRetryPolicy(policy);
        return this;
    }

    /**
     * If using parallelProcessing you can specify a custom thread pool to be
     * used. In fact also if you are not using parallelProcessing this custom
     * thread pool is used to send out aggregated exchanges as well.
     */
    @Override
    public AggregateDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * If using parallelProcessing you can specify a custom thread pool to be
     * used. In fact also if you are not using parallelProcessing this custom
     * thread pool is used to send out aggregated exchanges as well.
     */
    @Override
    public AggregateDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * If using either of the completionTimeout, completionTimeoutExpression, or
     * completionInterval options a background thread is created to check for
     * the completion for every aggregator. Set this option to provide a custom
     * thread pool to be used rather than creating a new thread for every
     * aggregator.
     */
    public AggregateDefinition timeoutCheckerExecutorService(ScheduledExecutorService executorService) {
        setTimeoutCheckerExecutorService(executorService);
        return this;
    }

    /**
     * If using either of the completionTimeout, completionTimeoutExpression, or
     * completionInterval options a background thread is created to check for
     * the completion for every aggregator. Set this option to provide a custom
     * thread pool to be used rather than creating a new thread for every
     * aggregator.
     */
    public AggregateDefinition timeoutCheckerExecutorService(Supplier<ScheduledExecutorService> executorService) {
        setTimeoutCheckerExecutorService(executorService.get());
        return this;
    }

    /**
     * If using either of the completionTimeout, completionTimeoutExpression, or
     * completionInterval options a background thread is created to check for
     * the completion for every aggregator. Set this option to provide a custom
     * thread pool to be used rather than creating a new thread for every
     * aggregator.
     */
    public AggregateDefinition timeoutCheckerExecutorServiceRef(String executorServiceRef) {
        setTimeoutCheckerExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * To use a {@link org.apache.camel.processor.aggregate.AggregateController}
     * to allow external sources to control this aggregator.
     */
    public AggregateDefinition aggregateController(AggregateController aggregateController) {
        setAggregateController(aggregateController);
        return this;
    }

    /**
     * To use a {@link org.apache.camel.processor.aggregate.AggregateController}
     * to allow external sources to control this aggregator.
     */
    public AggregateDefinition aggregateController(Supplier<AggregateController> aggregateController) {
        setAggregateController(aggregateController.get());
        return this;
    }

    // Section - Methods from ExpressionNode
    // Needed to copy methods from ExpressionNode here so that I could specify
    // the
    // correlation expression as optional in JAXB

    public ExpressionDefinition getExpression() {
        if (expression == null && correlationExpression != null) {
            expression = correlationExpression.getExpressionType();
        }
        return expression;
    }

    public void setExpression(ExpressionDefinition expression) {
        this.expression = expression;
    }

    public void setExpression(Expression expression) {
        setExpression(new ExpressionDefinition(expression));
    }

    protected void checkNoCompletedPredicate() {
        if (getCompletionPredicate() != null) {
            throw new IllegalArgumentException("There is already a completionPredicate defined for this aggregator: " + this);
        }
    }

    @Override
    public List<ProcessorDefinition<?>> getOutputs() {
        return outputs;
    }

    @XmlElementRef
    public void setOutputs(List<ProcessorDefinition<?>> outputs) {
        super.setOutputs(outputs);
    }

}
