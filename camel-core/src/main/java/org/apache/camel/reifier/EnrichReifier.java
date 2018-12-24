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
package org.apache.camel.reifier;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.Enricher;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.spi.RouteContext;

class EnrichReifier extends ExpressionReifier<EnrichDefinition> {

    EnrichReifier(ProcessorDefinition<?> definition) {
        super(EnrichDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {

        Expression exp = definition.getExpression().createExpression(routeContext);
        boolean isShareUnitOfWork = definition.getShareUnitOfWork() != null && definition.getShareUnitOfWork();
        boolean isIgnoreInvalidEndpoint = definition.getIgnoreInvalidEndpoint() != null && definition.getIgnoreInvalidEndpoint();

        Enricher enricher = new Enricher(exp);
        enricher.setShareUnitOfWork(isShareUnitOfWork);
        enricher.setIgnoreInvalidEndpoint(isIgnoreInvalidEndpoint);
        AggregationStrategy strategy = createAggregationStrategy(routeContext);
        if (strategy != null) {
            enricher.setAggregationStrategy(strategy);
        }
        if (definition.getAggregateOnException() != null) {
            enricher.setAggregateOnException(definition.getAggregateOnException());
        }
        return enricher;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = definition.getAggregationStrategy();
        if (strategy == null && definition.getAggregationStrategyRef() != null) {
            Object aggStrategy = routeContext.lookup(definition.getAggregationStrategyRef(), Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy) aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, definition.getAggregationStrategyMethodName());
                if (definition.getAggregationStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(definition.getAggregationStrategyMethodAllowNull());
                    adapter.setAllowNullOldExchange(definition.getAggregationStrategyMethodAllowNull());
                }
                strategy = adapter;
            } else {
                throw new IllegalArgumentException("Cannot find AggregationStrategy in Registry with name: " + definition.getAggregationStrategyRef());
            }
        }

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        return strategy;
    }

}
