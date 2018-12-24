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
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.spi.RouteContext;

class PollEnrichReifier extends ProcessorReifier<PollEnrichDefinition> {

    PollEnrichReifier(ProcessorDefinition<?> definition) {
        super((PollEnrichDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {

        // if no timeout then we should block, and there use a negative timeout
        long time = definition.getTimeout() != null ? definition.getTimeout() : -1;
        boolean isIgnoreInvalidEndpoint = definition.getIgnoreInvalidEndpoint() != null && definition.getIgnoreInvalidEndpoint();
        Expression exp = definition.getExpression().createExpression(routeContext);

        PollEnricher enricher = new PollEnricher(exp, time);

        AggregationStrategy strategy = createAggregationStrategy(routeContext);
        if (strategy == null) {
            enricher.setDefaultAggregationStrategy();
        } else {
            enricher.setAggregationStrategy(strategy);
        }
        if (definition.getAggregateOnException() != null) {
            enricher.setAggregateOnException(definition.getAggregateOnException());
        }
        if (definition.getCacheSize() != null) {
            enricher.setCacheSize(definition.getCacheSize());
        }
        enricher.setIgnoreInvalidEndpoint(isIgnoreInvalidEndpoint);

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
