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


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionClause;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Aggregator;
import org.apache.camel.processor.aggregate.AggregationCollection;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;aggregator/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "aggregator")
@XmlAccessorType(XmlAccessType.FIELD)
public class AggregatorType extends ExpressionNode {
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
    @XmlElement(name = "completedPredicate", required = false)
    private ExpressionSubElementType completedPredicate;

    public AggregatorType() {
    }

    public AggregatorType(Expression correlationExpression) {
        super(correlationExpression);
    }

    public AggregatorType(ExpressionType correlationExpression) {
        super(correlationExpression);
    }

    public AggregatorType(Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        super(correlationExpression);
        this.aggregationStrategy = aggregationStrategy;
    }

    @Override
    public String toString() {
        return "Aggregator[" + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "aggregator";
    }
    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return createAggregator(routeContext);
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

            Expression aggregateExpression = getExpression().createExpression(routeContext);

            Predicate predicate = null;
            if (getCompletedPredicate() != null) {
                predicate = getCompletedPredicate().createPredicate(routeContext);
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

    public void setCompletedPredicate(ExpressionSubElementType completedPredicate) {
        this.completedPredicate = completedPredicate;
    }

    public ExpressionSubElementType getCompletedPredicate() {
        return completedPredicate;
    }

    // Fluent API
    //-------------------------------------------------------------------------
    public AggregatorType batchSize(int batchSize) {
        setBatchSize(batchSize);
        return this;
    }

    public AggregatorType outBatchSize(int batchSize) {
        setOutBatchSize(batchSize);
        return this;
    }

    public AggregatorType batchTimeout(long batchTimeout) {
        setBatchTimeout(batchTimeout);
        return this;
    }

    public AggregatorType aggregationCollection(AggregationCollection aggregationCollection) {
        setAggregationCollection(aggregationCollection);
        return this;
    }

    public AggregatorType aggregationStrategy(AggregationStrategy aggregationStrategy) {
        setAggregationStrategy(aggregationStrategy);
        return this;
    }

    public AggregatorType strategyRef(String strategyRef) {
        setStrategyRef(strategyRef);
        return this;
    }

    /**
     * Sets the predicate used to determine if the aggregation is completed
     *
     * @return the clause used to create the predicate
     */
    public ExpressionClause<AggregatorType> completedPredicate() {
        checkNoCompletedPredicate();
        ExpressionClause<AggregatorType> clause = new ExpressionClause<AggregatorType>(this);
        setCompletedPredicate(new ExpressionSubElementType((Expression)clause));
        return clause;
    }

    /**
     * Sets the predicate used to determine if the aggregation is completed
     */
    public AggregatorType completedPredicate(Predicate predicate) {
        checkNoCompletedPredicate();
        setCompletedPredicate(new ExpressionSubElementType(predicate));
        return this;
    }

    protected void checkNoCompletedPredicate() {
        if (getCompletedPredicate() != null) {
            throw new IllegalArgumentException("There is already a completedPredicate defined for this aggregator: " + this);
        }
    }
}
