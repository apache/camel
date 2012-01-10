/**
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
import java.util.concurrent.ExecutorService;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.processor.UnitOfWorkProcessor;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.GroupedExchangeAggregationStrategy;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;aggregate/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "aggregate")
@XmlAccessorType(XmlAccessType.FIELD)
public class AggregateDefinition extends ProcessorDefinition<AggregateDefinition> implements ExecutorServiceAwareDefinition<AggregateDefinition> {
    @XmlElement(name = "correlationExpression", required = true)
    private ExpressionSubElementDefinition correlationExpression;
    @XmlElement(name = "completionPredicate")
    private ExpressionSubElementDefinition completionPredicate;
    @XmlElement(name = "completionTimeout")
    private ExpressionSubElementDefinition completionTimeoutExpression;
    @XmlElement(name = "completionSize")
    private ExpressionSubElementDefinition completionSizeExpression;
    @XmlTransient
    private ExpressionDefinition expression;
    @XmlElementRef
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
    @XmlTransient
    private AggregationRepository aggregationRepository;
    @XmlAttribute
    private Boolean parallelProcessing;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private String aggregationRepositoryRef;
    @XmlAttribute
    private String strategyRef;
    @XmlAttribute
    private Integer completionSize;
    @XmlAttribute
    private Long completionInterval;
    @XmlAttribute
    private Long completionTimeout;
    @XmlAttribute
    private Boolean completionFromBatchConsumer;
    @XmlAttribute
    private Boolean groupExchanges;
    @XmlAttribute
    private Boolean eagerCheckCompletion;
    @XmlAttribute
    private Boolean ignoreInvalidCorrelationKeys;
    @XmlAttribute
    private Integer closeCorrelationKeyOnCompletion;
    @XmlAttribute
    private Boolean discardOnCompletionTimeout;

    public AggregateDefinition() {
    }

    public AggregateDefinition(Predicate predicate) {
        if (predicate != null) {
            setExpression(new ExpressionDefinition(predicate));
        }
    }    
    
    public AggregateDefinition(Expression correlationExpression) {
        if (correlationExpression != null) {
            setExpression(new ExpressionDefinition(correlationExpression));
        }
    }

    public AggregateDefinition(ExpressionDefinition correlationExpression) {
        this.expression = correlationExpression;
    }

    public AggregateDefinition(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        this(correlationExpression);
        this.aggregationStrategy = aggregationStrategy;
    }

    @Override
    public String toString() {
        String expressionString = (getExpression() != null) ? getExpression().getLabel() : "";     
        return "Aggregate[" + expressionString + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "aggregate";
    }

    @Override
    public String getLabel() {
        return "aggregate";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return createAggregator(routeContext);
    }

    public ExpressionClause<AggregateDefinition> createAndSetExpression() {
        ExpressionClause<AggregateDefinition> clause = new ExpressionClause<AggregateDefinition>(this);
        this.setExpression(clause);
        return clause;
    }

    protected AggregateProcessor createAggregator(RouteContext routeContext) throws Exception {
        Processor processor = this.createChildProcessor(routeContext, true);
        // wrap the aggregated route in a unit of work processor
        processor = new UnitOfWorkProcessor(routeContext, processor);

        Expression correlation = getExpression().createExpression(routeContext);
        AggregationStrategy strategy = createAggregationStrategy(routeContext);

        // executor service is mandatory for the Aggregator
        executorService = ExecutorServiceHelper.getConfiguredExecutorService(routeContext, "Aggregator", this);
        if (executorService == null) {
            if (isParallelProcessing()) {
                // we are running in parallel so create a cached thread pool which grows/shrinks automatic
                executorService = routeContext.getCamelContext().getExecutorServiceStrategy().newDefaultThreadPool(this, "Aggregator");
            } else {
                // use a synchronous thread pool if we are not running in parallel (will always use caller thread)
                executorService = routeContext.getCamelContext().getExecutorServiceStrategy().newSynchronousThreadPool(this, "Aggregator");
            }
        }
        AggregateProcessor answer = new AggregateProcessor(routeContext.getCamelContext(), processor, correlation, strategy, executorService);

        AggregationRepository repository = createAggregationRepository(routeContext);
        if (repository != null) {
            answer.setAggregationRepository(repository);
        }

        // set other options
        answer.setParallelProcessing(isParallelProcessing());
        if (getCompletionPredicate() != null) {
            Predicate predicate = getCompletionPredicate().createPredicate(routeContext);
            answer.setCompletionPredicate(predicate);
        }
        if (getCompletionTimeoutExpression() != null) {
            Expression expression = getCompletionTimeoutExpression().createExpression(routeContext);
            answer.setCompletionTimeoutExpression(expression);
        }
        if (getCompletionTimeout() != null) {
            answer.setCompletionTimeout(getCompletionTimeout());
        }
        if (getCompletionInterval() != null) {
            answer.setCompletionInterval(getCompletionInterval());
        }
        if (getCompletionSizeExpression() != null) {
            Expression expression = getCompletionSizeExpression().createExpression(routeContext);
            answer.setCompletionSizeExpression(expression);
        }
        if (getCompletionSize() != null) {
            answer.setCompletionSize(getCompletionSize());
        }
        if (getCompletionFromBatchConsumer() != null) {
            answer.setCompletionFromBatchConsumer(isCompletionFromBatchConsumer());
        }
        if (getEagerCheckCompletion() != null) {
            answer.setEagerCheckCompletion(isEagerCheckCompletion());
        }
        if (getIgnoreInvalidCorrelationKeys() != null) {
            answer.setIgnoreInvalidCorrelationKeys(isIgnoreInvalidCorrelationKeys());
        }
        if (getCloseCorrelationKeyOnCompletion() != null) {
            answer.setCloseCorrelationKeyOnCompletion(getCloseCorrelationKeyOnCompletion());
        }
        if (getDiscardOnCompletionTimeout() != null) {
            answer.setDiscardOnCompletionTimeout(isDiscardOnCompletionTimeout());
        }

        return answer;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = getAggregationStrategy();
        if (strategy == null && strategyRef != null) {
            strategy = routeContext.lookup(strategyRef, AggregationStrategy.class);
        }

        if (groupExchanges != null && groupExchanges) {
            if (strategy != null || strategyRef != null) {
                throw new IllegalArgumentException("Options groupExchanges and AggregationStrategy cannot be enabled at the same time");
            }
            // if grouped exchange is enabled then use special strategy for that
            strategy = new GroupedExchangeAggregationStrategy();
        }

        if (strategy == null) {
            throw new IllegalArgumentException("AggregationStrategy or AggregationStrategyRef must be set on " + this);
        }
        return strategy;
    }

    private AggregationRepository createAggregationRepository(RouteContext routeContext) {
        AggregationRepository repository = getAggregationRepository();
        if (repository == null && aggregationRepositoryRef != null) {
            repository = routeContext.lookup(aggregationRepositoryRef, AggregationRepository.class);
            if (repository == null) {
                throw new IllegalArgumentException("AggregationRepositoryRef " + aggregationRepositoryRef + " not found in registry.");
            }
        }
        return repository;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public String getAggregationStrategyRef() {
        return strategyRef;
    }

    public void setAggregationStrategyRef(String aggregationStrategyRef) {
        this.strategyRef = aggregationStrategyRef;
    }

    public Integer getCompletionSize() {
        return completionSize;
    }

    public void setCompletionSize(Integer completionSize) {
        this.completionSize = completionSize;
    }

    public Long getCompletionInterval() {
        return completionInterval;
    }

    public void setCompletionInterval(Long completionInterval) {
        this.completionInterval = completionInterval;
    }

    public Long getCompletionTimeout() {
        return completionTimeout;
    }

    public void setCompletionTimeout(Long completionTimeout) {
        this.completionTimeout = completionTimeout;
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

    public void setCompletionTimeoutExpression(ExpressionSubElementDefinition completionTimeoutExpression) {
        this.completionTimeoutExpression = completionTimeoutExpression;
    }

    public ExpressionSubElementDefinition getCompletionSizeExpression() {
        return completionSizeExpression;
    }

    public void setCompletionSizeExpression(ExpressionSubElementDefinition completionSizeExpression) {
        this.completionSizeExpression = completionSizeExpression;
    }

    public Boolean getGroupExchanges() {
        return groupExchanges;
    }

    public boolean isGroupExchanges() {
        return groupExchanges != null && groupExchanges;
    }

    public void setGroupExchanges(Boolean groupExchanges) {
        this.groupExchanges = groupExchanges;
    }

    public Boolean getCompletionFromBatchConsumer() {
        return completionFromBatchConsumer;
    }

    public boolean isCompletionFromBatchConsumer() {
        return completionFromBatchConsumer != null && completionFromBatchConsumer;
    }

    public void setCompletionFromBatchConsumer(Boolean completionFromBatchConsumer) {
        this.completionFromBatchConsumer = completionFromBatchConsumer;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public Boolean getParallelProcessing() {
        return parallelProcessing;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing != null && parallelProcessing;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public String getStrategyRef() {
        return strategyRef;
    }

    public void setStrategyRef(String strategyRef) {
        this.strategyRef = strategyRef;
    }

    public Boolean getEagerCheckCompletion() {
        return eagerCheckCompletion;
    }

    public boolean isEagerCheckCompletion() {
        return eagerCheckCompletion != null && eagerCheckCompletion;
    }

    public void setEagerCheckCompletion(Boolean eagerCheckCompletion) {
        this.eagerCheckCompletion = eagerCheckCompletion;
    }

    public Boolean getIgnoreInvalidCorrelationKeys() {
        return ignoreInvalidCorrelationKeys;
    }

    public boolean isIgnoreInvalidCorrelationKeys() {
        return ignoreInvalidCorrelationKeys != null && ignoreInvalidCorrelationKeys;
    }

    public void setIgnoreInvalidCorrelationKeys(Boolean ignoreInvalidCorrelationKeys) {
        this.ignoreInvalidCorrelationKeys = ignoreInvalidCorrelationKeys;
    }

    public Integer getCloseCorrelationKeyOnCompletion() {
        return closeCorrelationKeyOnCompletion;
    }

    public void setCloseCorrelationKeyOnCompletion(Integer closeCorrelationKeyOnCompletion) {
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

    public Boolean getDiscardOnCompletionTimeout() {
        return discardOnCompletionTimeout;
    }

    public boolean isDiscardOnCompletionTimeout() {
        return discardOnCompletionTimeout != null && discardOnCompletionTimeout;
    }

    public void setDiscardOnCompletionTimeout(Boolean discardOnCompletionTimeout) {
        this.discardOnCompletionTimeout = discardOnCompletionTimeout;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * Use eager completion checking which means that the {{completionPredicate}} will use the incoming Exchange.
     * At opposed to without eager completion checking the {{completionPredicate}} will use the aggregated Exchange.
     *
     * @return builder
     */
    public AggregateDefinition eagerCheckCompletion() {
        setEagerCheckCompletion(true);
        return this;
    }

    /**
     * If a correlation key cannot be successfully evaluated it will be ignored by logging a {{DEBUG}} and then just
     * ignore the incoming Exchange.
     *
     * @return builder
     */
    public AggregateDefinition ignoreInvalidCorrelationKeys() {
        setIgnoreInvalidCorrelationKeys(true);
        return this;
    }

    /**
     * Closes a correlation key when its complete. Any <i>late</i> received exchanges which has a correlation key
     * that has been closed, it will be defined and a {@link org.apache.camel.processor.aggregate.ClosedCorrelationKeyException}
     * is thrown.
     *
     * @param capacity the maximum capacity of the closed correlation key cache.
     *                 Use <tt>0</tt> or negative value for unbounded capacity.
     * @return builder
     */
    public AggregateDefinition closeCorrelationKeyOnCompletion(int capacity) {
        setCloseCorrelationKeyOnCompletion(capacity);
        return this;
    }

    /**
     * Discards the aggregated message on completion timeout.
     * <p/>
     * This means on timeout the aggregated message is dropped and not sent out of the aggregator.
     *
     * @return builder
     */
    public AggregateDefinition discardOnCompletionTimeout() {
        setDiscardOnCompletionTimeout(true);
        return this;
    }

    /**
     * Enables the batch completion mode where we aggregate from a {@link org.apache.camel.BatchConsumer}
     * and aggregate the total number of exchanges the {@link org.apache.camel.BatchConsumer} has reported
     * as total by checking the exchange property {@link org.apache.camel.Exchange#BATCH_COMPLETE} when its complete.
     *
     * @return builder
     */
    public AggregateDefinition completionFromBatchConsumer() {
        setCompletionFromBatchConsumer(true);
        return this;
    }

    /**
     * Sets the completion size, which is the number of aggregated exchanges which would
     * cause the aggregate to consider the group as complete and send out the aggregated exchange.
     *
     * @param completionSize  the completion size
     * @return builder
     */
    public AggregateDefinition completionSize(int completionSize) {
        setCompletionSize(completionSize);
        return this;
    }

    /**
     * Sets the completion size, which is the number of aggregated exchanges which would
     * cause the aggregate to consider the group as complete and send out the aggregated exchange.
     *
     * @param completionSize  the completion size as an {@link org.apache.camel.Expression} which is evaluated as a {@link Integer} type
     * @return builder
     */
    public AggregateDefinition completionSize(Expression completionSize) {
        setCompletionSizeExpression(new ExpressionSubElementDefinition(completionSize));
        return this;
    }

    /**
     * Sets the completion interval, which would cause the aggregate to consider the group as complete
     * and send out the aggregated exchange.
     *
     * @param completionInterval  the interval in millis
     * @return the builder
     */
    public AggregateDefinition completionInterval(long completionInterval) {
        setCompletionInterval(completionInterval);
        return this;
    }

    /**
     * Sets the completion timeout, which would cause the aggregate to consider the group as complete
     * and send out the aggregated exchange.
     *
     * @param completionTimeout  the timeout in millis
     * @return the builder
     */
    public AggregateDefinition completionTimeout(long completionTimeout) {
        setCompletionTimeout(completionTimeout);
        return this;
    }

    /**
     * Sets the completion timeout, which would cause the aggregate to consider the group as complete
     * and send out the aggregated exchange.
     *
     * @param completionTimeout  the timeout as an {@link Expression} which is evaluated as a {@link Long} type
     * @return the builder
     */
    public AggregateDefinition completionTimeout(Expression completionTimeout) {
        setCompletionTimeoutExpression(new ExpressionSubElementDefinition(completionTimeout));
        return this;
    }

    /**
     * Sets the aggregate strategy to use
     *
     * @param aggregationStrategy  the aggregate strategy to use
     * @return the builder
     */
    public AggregateDefinition aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    /**
     * Sets the aggregate strategy to use
     *
     * @param aggregationStrategyRef  reference to the strategy to lookup in the registry
     * @return the builder
     */
    public AggregateDefinition aggregationStrategyRef(String aggregationStrategyRef) {
        setAggregationStrategyRef(aggregationStrategyRef);
        return this;
    }

    /**
     * Sets the custom aggregate repository to use.
     * <p/>
     * Will by default use {@link org.apache.camel.processor.aggregate.MemoryAggregationRepository}
     *
     * @param aggregationRepository  the aggregate repository to use
     * @return the builder
     */
    public AggregateDefinition aggregationRepository(AggregationRepository aggregationRepository) {
        setAggregationRepository(aggregationRepository);
        return this;
    }

    /**
     * Sets the custom aggregate repository to use
     * <p/>
     * Will by default use {@link org.apache.camel.processor.aggregate.MemoryAggregationRepository}
     *
     * @param aggregationRepositoryRef  reference to the repository to lookup in the registry
     * @return the builder
     */
    public AggregateDefinition aggregationRepositoryRef(String aggregationRepositoryRef) {
        setAggregationRepositoryRef(aggregationRepositoryRef);
        return this;
    }

    /**
     * Enables grouped exchanges, so the aggregator will group all aggregated exchanges into a single
     * combined Exchange holding all the aggregated exchanges in a {@link java.util.List} as a exchange
     * property with the key {@link org.apache.camel.Exchange#GROUPED_EXCHANGE}.
     *
     * @return the builder
     */
    public AggregateDefinition groupExchanges() {
        setGroupExchanges(true);
        return this;
    }

    /**
     * Sets the predicate used to determine if the aggregation is completed
     *
     * @return the clause used to create the predicate
     */
    public ExpressionClause<AggregateDefinition> completionPredicate() {
        checkNoCompletedPredicate();
        ExpressionClause<AggregateDefinition> clause = new ExpressionClause<AggregateDefinition>(this);
        setCompletionPredicate(new ExpressionSubElementDefinition((Expression)clause));
        return clause;
    }

    /**
     * Sets the predicate used to determine if the aggregation is completed
     *
     * @param predicate  the predicate
     * @return the builder
     */
    public AggregateDefinition completionPredicate(Predicate predicate) {
        checkNoCompletedPredicate();
        setCompletionPredicate(new ExpressionSubElementDefinition(predicate));
        return this;
    }

    /**
     * Sending the aggregated output in parallel
     *
     * @return the builder
     */
    public AggregateDefinition parallelProcessing() {
        setParallelProcessing(true);
        return this;
    }
    
    public AggregateDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    public AggregateDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    protected void checkNoCompletedPredicate() {
        if (getCompletionPredicate() != null) {
            throw new IllegalArgumentException("There is already a completionPredicate defined for this aggregator: " + this);
        }
    }

    public void setCorrelationExpression(ExpressionSubElementDefinition correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    public ExpressionSubElementDefinition getCorrelationExpression() {
        return correlationExpression;
    }

    // Section - Methods from ExpressionNode
    // Needed to copy methods from ExpressionNode here so that I could specify the
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

    public List<ProcessorDefinition> getOutputs() {
        return outputs;
    }

    public boolean isOutputSupported() {
        return true;
    }

    public void setOutputs(List<ProcessorDefinition> outputs) {
        this.outputs = outputs;
    }

}
