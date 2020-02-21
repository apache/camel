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
package org.apache.camel.reifier;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.Enricher;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;

public class EnrichReifier extends ExpressionReifier<EnrichDefinition> {

    public EnrichReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, EnrichDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        Expression exp = createExpression(definition.getExpression());
        boolean isShareUnitOfWork = parseBoolean(definition.getShareUnitOfWork(), false);
        boolean isIgnoreInvalidEndpoint = parseBoolean(definition.getIgnoreInvalidEndpoint(), false);

        Enricher enricher = new Enricher(exp);
        enricher.setShareUnitOfWork(isShareUnitOfWork);
        enricher.setIgnoreInvalidEndpoint(isIgnoreInvalidEndpoint);
        if (definition.getCacheSize() != null) {
            enricher.setCacheSize(parseInt(definition.getCacheSize()));
        }
        AggregationStrategy strategy = createAggregationStrategy();
        if (strategy != null) {
            enricher.setAggregationStrategy(strategy);
        }
        if (definition.getAggregateOnException() != null) {
            enricher.setAggregateOnException(parseBoolean(definition.getAggregateOnException(), false));
        }

        return enricher;
    }

    private AggregationStrategy createAggregationStrategy() {
        AggregationStrategy strategy = definition.getAggregationStrategy();
        if (strategy == null && definition.getAggregationStrategyRef() != null) {
            Object aggStrategy = lookup(definition.getAggregationStrategyRef(), Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy)aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, definition.getAggregationStrategyMethodName());
                if (definition.getAggregationStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(parseBoolean(definition.getAggregationStrategyMethodAllowNull(), false));
                    adapter.setAllowNullOldExchange(parseBoolean(definition.getAggregationStrategyMethodAllowNull(), false));
                }
                strategy = adapter;
            } else {
                throw new IllegalArgumentException("Cannot find AggregationStrategy in Registry with name: " + definition.getAggregationStrategyRef());
            }
        }

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware)strategy).setCamelContext(camelContext);
        }

        return strategy;
    }

}
