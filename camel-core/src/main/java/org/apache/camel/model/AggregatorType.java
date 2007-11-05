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
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.impl.RouteContext;
import org.apache.camel.model.language.ExpressionType;
import org.apache.camel.processor.Aggregator;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

/**
 * @version $Revision: 1.1 $
 */
@XmlRootElement(name = "aggregator")
@XmlAccessorType(XmlAccessType.FIELD)
public class AggregatorType extends ExpressionNode {
    @XmlTransient
    private AggregationStrategy aggregationStrategy;
    @XmlAttribute(required = false)
    private Integer batchSize;
    @XmlAttribute(required = false)
    private Long batchTimeout;
    @XmlAttribute(required = false)
    private String strategyRef;

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
    public void addRoutes(RouteContext routeContext, Collection<Route> routes) throws Exception {
        Endpoint from = routeContext.getEndpoint();
        final Processor processor = routeContext.createProcessor(this);
        AggregationStrategy strategy = getAggregationStrategy();
        if (strategy == null && strategyRef != null) {
            strategy = routeContext.lookup(strategyRef, AggregationStrategy.class);
        }
        if (strategy == null) {
            strategy = new UseLatestAggregationStrategy();
        }
        final Aggregator service = new Aggregator(from, processor, getExpression()
                .createExpression(routeContext), strategy);

        if (batchSize != null) {
            service.setBatchSize(batchSize);
        }
        if (batchSize != null) {
            service.setBatchTimeout(batchTimeout);
        }

        Route route = new Route<Exchange>(from, service) {
            @Override
            public String toString() {
                return "AggregatorRoute[" + getEndpoint() + " -> " + processor + "]";
            }
        };

        routes.add(route);
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
}
