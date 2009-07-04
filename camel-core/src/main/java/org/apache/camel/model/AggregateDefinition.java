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
import org.apache.camel.processor.Aggregator;
import org.apache.camel.processor.aggregate.AggregationCollection;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;aggregate/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "aggregate")
@XmlAccessorType(XmlAccessType.FIELD)
public class AggregateDefinition extends ProcessorDefinition<AggregateDefinition> {
    @XmlElement(name = "correlationExpression", required = false)
    private ExpressionSubElementDefinition correlationExpression;
    @XmlTransient
    private ExpressionDefinition expression;
    @XmlElementRef
    private List<ProcessorDefinition> outputs = new ArrayList<ProcessorDefinition>();
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlTransient
    private AggregationCollection aggregationCollection;
    @XmlAttribute(required = false)
    private Integer batchSize;
    @XmlAttribute(required = false)
    private Integer outBatchSize;
    @XmlAttribute(required = false)
    private Long batchTimeout;
    @XmlAttribute(required = false)
    private String strategyRef;
    @XmlAttribute(required = false)
    private String collectionRef;    
    @XmlAttribute(required = false)
    private Boolean groupExchanges;
    @XmlAttribute(required = false)
    private Boolean batchSizeFromConsumer;
    @XmlElement(name = "completionPredicate", required = false)
    private ExpressionSubElementDefinition completionPredicate;

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
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return createAggregator(routeContext);
    }

    public ExpressionClause<AggregateDefinition> createAndSetExpression() {
        ExpressionClause<AggregateDefinition> clause = new ExpressionClause<AggregateDefinition>(this);
        this.setExpression(clause);
        return clause;
    }
    
    protected Aggregator createAggregator(RouteContext routeContext) throws Exception {
        final Processor processor = routeContext.createProcessor(this);

        final Aggregator aggregator;
        if (getAggregationCollection() == null) {
            setAggregationCollection(createAggregationCollection(routeContext));
        }
        
        if (aggregationCollection != null) {
            // create the aggregator using the collection
            // pre configure the collection if its expression and strategy is not set, then
            // use the ones that is pre configured with this type
            if (aggregationCollection.getCorrelationExpression() == null) {
                aggregationCollection.setCorrelationExpression(getExpression());
            }
            if (aggregationCollection.getAggregationStrategy() == null) {
                AggregationStrategy strategy = createAggregationStrategy(routeContext);
                aggregationCollection.setAggregationStrategy(strategy);
            }
            aggregator = new Aggregator(processor, aggregationCollection);
        } else {
            // create the aggregator using a default collection
            AggregationStrategy strategy = createAggregationStrategy(routeContext);

            if (getExpression() == null) {
                throw new IllegalArgumentException("You need to specify an expression or "
                                                   + "aggregation collection for this aggregator: " + this);
            }
            
            Expression aggregateExpression = getExpression().createExpression(routeContext);           

            Predicate predicate = null;
            if (getCompletionPredicate() != null) {
                predicate = getCompletionPredicate().createPredicate(routeContext);
            }
            if (predicate != null) {
                aggregator = new Aggregator(processor, aggregateExpression, strategy, predicate);
            } else {
                aggregator = new Aggregator(processor, aggregateExpression, strategy);
            }
        }
        
        if (batchSize != null) {
            aggregator.setBatchSize(batchSize);
        }
        if (batchTimeout != null) {
            aggregator.setBatchTimeout(batchTimeout);
        }
        if (outBatchSize != null) {
            aggregator.setOutBatchSize(outBatchSize);
        }
        if (groupExchanges != null) {
            aggregator.setGroupExchanges(groupExchanges);
        }
        if (batchSizeFromConsumer != null) {
            aggregator.setBatchConsumer(batchSizeFromConsumer);
        }

        return aggregator;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = getAggregationStrategy();
        if (strategy == null && strategyRef != null) {
            strategy = routeContext.lookup(strategyRef, AggregationStrategy.class);
        }
        if (strategy == null) {
            // fallback to use latest
            strategy = new UseLatestAggregationStrategy();
        }
        return strategy;
    }

    private AggregationCollection createAggregationCollection(RouteContext routeContext) {
        AggregationCollection collection = getAggregationCollection();
        if (collection == null && collectionRef != null) {
            collection = routeContext.lookup(collectionRef, AggregationCollection.class);
        }
        return collection;
    }    
    
    public AggregationCollection getAggregationCollection() {
        return aggregationCollection;
    }

    public void setAggregationCollection(AggregationCollection aggregationCollection) {
        this.aggregationCollection = aggregationCollection;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }

    public Integer getOutBatchSize() {
        return outBatchSize;
    }

    public void setOutBatchSize(Integer outBatchSize) {
        this.outBatchSize = outBatchSize;
    }

    public Long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(Long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    public String getStrategyRef() {
        return strategyRef;
    }

    public void setStrategyRef(String strategyRef) {
        this.strategyRef = strategyRef;
    }

    public String getCollectionRef() {
        return collectionRef;
    }

    public void setCollectionRef(String collectionRef) {
        this.collectionRef = collectionRef;
    }

    public void setCompletionPredicate(ExpressionSubElementDefinition completionPredicate) {
        this.completionPredicate = completionPredicate;
    }

    public ExpressionSubElementDefinition getCompletionPredicate() {
        return completionPredicate;
    }

    public Boolean getGroupExchanges() {
        return groupExchanges;
    }

    public void setGroupExchanges(Boolean groupExchanges) {
        this.groupExchanges = groupExchanges;
    }

    public Boolean getBatchSizeFromConsumer() {
        return batchSizeFromConsumer;
    }

    public void setBatchSizeFromConsumer(Boolean batchSizeFromConsumer) {
        this.batchSizeFromConsumer = batchSizeFromConsumer;
    }

    // Fluent API
    //-------------------------------------------------------------------------

    /**
     * Enables the batch completion mode where we aggregate from a {@link org.apache.camel.BatchConsumer}
     * and aggregate the total number of exchanges the {@link org.apache.camel.BatchConsumer} has reported
     * as total by setting the exchange property {@link org.apache.camel.Exchange#BATCH_SIZE}.
     *
     * @return builder
     */
    public AggregateDefinition batchSizeFromConsumer() {
        setBatchSizeFromConsumer(true);
        return this;
    }

    /**
     * Sets the in batch size for number of exchanges received
     *
     * @param batchSize  the batch size
     * @return builder
     */
    public AggregateDefinition batchSize(int batchSize) {
        setBatchSize(batchSize);
        return this;
    }

    /**
     * Sets the out batch size for number of exchanges sent
     *
     * @param batchSize  the batch size
     * @return builder
     */
    public AggregateDefinition outBatchSize(int batchSize) {
        setOutBatchSize(batchSize);
        return this;
    }

    /**
     * Sets the batch timeout
     *
     * @param batchTimeout  the timeout in millis
     * @return the builder
     */
    public AggregateDefinition batchTimeout(long batchTimeout) {
        setBatchTimeout(batchTimeout);
        return this;
    }

    /**
     * Sets the aggregate collection to use
     *
     * @param aggregationCollection  the aggregate collection to use
     * @return the builder
     */
    public AggregateDefinition aggregationCollection(AggregationCollection aggregationCollection) {
        setAggregationCollection(aggregationCollection);
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
     * Sets the aggregate collection to use
     *
     * @param collectionRef  reference to the aggregate collection to lookup in the registry
     * @return the builder
     */
    public AggregateDefinition collectionRef(String collectionRef) {
        setCollectionRef(collectionRef);
        return this;
    }

    /**
     * Sets the aggregate strategy to use
     *
     * @param strategyRef  reference to the strategy to lookup in the registry
     * @return the builder
     */
    public AggregateDefinition strategyRef(String strategyRef) {
        setStrategyRef(strategyRef);
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
     */
    public AggregateDefinition completionPredicate(Predicate predicate) {
        checkNoCompletedPredicate();
        setCompletionPredicate(new ExpressionSubElementDefinition(predicate));
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
