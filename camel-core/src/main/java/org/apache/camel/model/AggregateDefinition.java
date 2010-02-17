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
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;aggregate/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "aggregate")
@XmlAccessorType(XmlAccessType.FIELD)
public class AggregateDefinition extends ProcessorDefinition<AggregateDefinition> {
    @XmlElement(name = "correlationExpression", required = true)
    private ExpressionSubElementDefinition correlationExpression;
    @XmlElement(name = "completionPredicate", required = false)
    private ExpressionSubElementDefinition completionPredicate;
    @XmlTransient
    private ExpressionDefinition expression;
    @XmlElementRef
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute(required = false)
    private Boolean parallelProcessing;
    @XmlAttribute(required = false)
    private String executorServiceRef;
    @XmlAttribute(required = true)
    private String strategyRef;
    @XmlAttribute(required = false)
    private Integer completionSize;
    @XmlAttribute(required = false)
    private Long completionTimeout;
    @XmlAttribute(required = false)
    private Boolean completionFromBatchConsumer;
    @XmlAttribute(required = false)
    private Boolean groupExchanges;
    @XmlAttribute(required = false)
    private Boolean eagerCheckCompletion;
    @XmlAttribute(required = false)
    private Boolean ignoreBadCorrelationKeys;
    @XmlAttribute(required = false)
    private Integer closeCorrelationKeyOnCompletion;

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
        Processor processor = routeContext.createProcessor(this);
        // wrap the aggregated route in a unit of work processor
        processor = new UnitOfWorkProcessor(routeContext, processor);

        Expression correlation = getExpression().createExpression(routeContext);
        AggregationStrategy strategy = createAggregationStrategy(routeContext);

        AggregateProcessor answer = new AggregateProcessor(processor, correlation, strategy);

        ExecutorService executor = createExecutorService(routeContext);
        answer.setExecutorService(executor);
        if (isParallelProcessing() != null) {
            answer.setParallelProcessing(isParallelProcessing());
        }

        if (getCompletionPredicate() != null) {
            Predicate predicate = getCompletionPredicate().createPredicate(routeContext);
            answer.setCompletionPredicate(predicate);
        }
        if (getCompletionSize() != null) {
            answer.setCompletionSize(getCompletionSize());
        }
        if (getCompletionTimeout() != null) {
            answer.setCompletionTimeout(getCompletionTimeout());
        }
        if (isCompletionFromBatchConsumer() != null) {
            answer.setCompletionFromBatchConsumer(isCompletionFromBatchConsumer());
        }
        if (isEagerCheckCompletion() != null) {
            answer.setEagerCheckCompletion(isEagerCheckCompletion());
        }
        if (isIgnoreBadCorrelationKeys() != null) {
            answer.setIgnoreBadCorrelationKeys(isIgnoreBadCorrelationKeys());
        }
        if (getCloseCorrelationKeyOnCompletion() != null) {
            answer.setCloseCorrelationKeyOnCompletion(getCloseCorrelationKeyOnCompletion());
        }

        return answer;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = getAggregationStrategy();
        if (strategy == null && strategyRef != null) {
            strategy = routeContext.lookup(strategyRef, AggregationStrategy.class);
        }
        if (strategy == null && groupExchanges != null && groupExchanges) {
            // if grouped exchange is enabled then use special strategy for that
            strategy = new GroupedExchangeAggregationStrategy();
        }
        if (strategy == null) {
            throw new IllegalArgumentException("AggregationStrategy or AggregationStrategyRef must be set on " + this);
        }
        return strategy;
    }

    private ExecutorService createExecutorService(RouteContext routeContext) {
        if (executorService == null && executorServiceRef != null) {
            executorService = routeContext.lookup(executorServiceRef, ExecutorService.class);
        }
        return executorService;
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

    public Boolean isGroupExchanges() {
        return groupExchanges;
    }

    public void setGroupExchanges(Boolean groupExchanges) {
        this.groupExchanges = groupExchanges;
    }

    public Boolean isCompletionFromBatchConsumer() {
        return completionFromBatchConsumer;
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

    public Boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(Boolean parallelProcessing) {
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

    public Boolean isEagerCheckCompletion() {
        return eagerCheckCompletion;
    }

    public void setEagerCheckCompletion(Boolean eagerCheckCompletion) {
        this.eagerCheckCompletion = eagerCheckCompletion;
    }

    public Boolean isIgnoreBadCorrelationKeys() {
        return ignoreBadCorrelationKeys;
    }

    public void setIgnoreBadCorrelationKeys(Boolean ignoreBadCorrelationKeys) {
        this.ignoreBadCorrelationKeys = ignoreBadCorrelationKeys;
    }

    public Integer getCloseCorrelationKeyOnCompletion() {
        return closeCorrelationKeyOnCompletion;
    }

    public void setCloseCorrelationKeyOnCompletion(Integer closeCorrelationKeyOnCompletion) {
        this.closeCorrelationKeyOnCompletion = closeCorrelationKeyOnCompletion;
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
    public AggregateDefinition ignoreBadCorrelationKeys() {
        setIgnoreBadCorrelationKeys(true);
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

    /**
     * Setting the executor service for executing the sending the aggregated output.
     *
     * @param executorService the executor service
     * @return the builder
     */
    public AggregateDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Setting the executor service for executing the sending the aggregated output.
     *
     * @param executorServiceRef reference to the executor service
     * @return the builder
     */
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

    public void setOutputs(List<ProcessorDefinition> outputs) {
        this.outputs = outputs;
    }
}
