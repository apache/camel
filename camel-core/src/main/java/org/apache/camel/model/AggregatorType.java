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

import java.util.Collection;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
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
    private Long batchTimeout;
    @XmlAttribute(required = false)
    private String strategyRef;
    @XmlElement(name = "completedPredicate", required = false)
    private CompletedPredicate completedPredicate;

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
        return "Aggregator[ " + getExpression() + " -> " + getOutputs() + "]";
    }

    @Override
    public String getShortName() {
        return "aggregator";
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        final Aggregator aggregator = createAggregator(routeContext);
        doAddRoute(routeContext, routes, aggregator);
    }
    
    private void doAddRoute(RouteContext routeContext, Collection<Route> routes, final Aggregator aggregator)
        throws Exception {
        Route route = new Route<Exchange>(aggregator.getEndpoint(), aggregator) {
            @Override
            public String toString() {
                return "AggregatorRoute[" + getEndpoint() + " -> " + aggregator.getProcessor() + "]";
            }
        };

        routes.add(route);
    }
 
    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        final Aggregator aggregator = createAggregator(routeContext);
        
        doAddRoute(routeContext, routeContext.getCamelContext().getRoutes(), aggregator);
        routeContext.setIsRouteAdded(true);
        return aggregator;
    }

    protected Aggregator createAggregator(RouteContext routeContext) throws Exception {
        Endpoint from = routeContext.getEndpoint();
        final Processor processor = routeContext.createProcessor(this);

        final Aggregator aggregator;
        if (aggregationCollection != null) {
            aggregator = new Aggregator(from, processor, aggregationCollection);
        } else {
            AggregationStrategy strategy = getAggregationStrategy();
            if (strategy == null && strategyRef != null) {
                strategy = routeContext.lookup(strategyRef, AggregationStrategy.class);
            }
            if (strategy == null) {
                strategy = new UseLatestAggregationStrategy();
            }
            Expression aggregateExpression = getExpression().createExpression(routeContext);

            Predicate predicate = null;
            if (completedPredicate != null) {
                predicate = completedPredicate.createPredicate(routeContext);
            }
            if (predicate != null) {
                aggregator = new Aggregator(from, processor, aggregateExpression, strategy, predicate);
            } else {
                aggregator = new Aggregator(from, processor, aggregateExpression, strategy);
            }
        }
        
        if (batchSize != null) {
            aggregator.setBatchSize(batchSize);
        }
        
        if (batchTimeout != null) {
            aggregator.setBatchTimeout(batchTimeout);
        }
        
        return aggregator;
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

    public CompletedPredicate getCompletePredicate() {
        return completedPredicate;
    }

    public void setCompletePredicate(CompletedPredicate completedPredicate) {
        this.completedPredicate = completedPredicate;
    }

    // Fluent API
    //-------------------------------------------------------------------------
    public AggregatorType batchSize(int batchSize) {
        setBatchSize(batchSize);
        return this;
    }

    public AggregatorType batchTimeout(long batchTimeout) {
        setBatchTimeout(batchTimeout);
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
        completedPredicate = new CompletedPredicate(clause);
        return clause;
    }

    /**
     * Sets the predicate used to determine if the aggregation is completed
     */
    public AggregatorType completedPredicate(Predicate predicate) {
        checkNoCompletedPredicate();
        completedPredicate = new CompletedPredicate(predicate);
        return this;
    }

    protected void checkNoCompletedPredicate() {
        if (completedPredicate != null) {
            throw new IllegalArgumentException("There already is a completedPredicate defined for this aggregator: " + this);
        }
    }
}
