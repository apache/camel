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

import java.util.function.BiFunction;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.AggregationStrategyBiFunctionAdapter;
import org.apache.camel.support.DefaultExchange;

public class PollEnrichReifier extends ProcessorReifier<PollEnrichDefinition> {

    public PollEnrichReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (PollEnrichDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {

        // if no timeout then we should block, and there use a negative timeout
        long time = parseDuration(definition.getTimeout(), -1);
        boolean isIgnoreInvalidEndpoint = parseBoolean(definition.getIgnoreInvalidEndpoint(), false);
        boolean isAggregateOnException = parseBoolean(definition.getAggregateOnException(), false);

        PollEnricher enricher;
        if (definition.getExpression() instanceof ConstantExpression) {
            Expression exp = createExpression(definition.getExpression());
            Exchange ex = new DefaultExchange(camelContext);
            String dest = exp.evaluate(ex, String.class);
            enricher = new PollEnricher(dest, time);
        } else {
            Expression exp = createExpression(definition.getExpression());
            enricher = new PollEnricher(exp, time);
        }

        AggregationStrategy strategy = createAggregationStrategy();
        if (strategy != null) {
            enricher.setAggregationStrategy(strategy);
        }
        Integer num = parseInt(definition.getCacheSize());
        if (num != null) {
            enricher.setCacheSize(num);
        }
        enricher.setIgnoreInvalidEndpoint(isIgnoreInvalidEndpoint);
        enricher.setAggregateOnException(isAggregateOnException);

        return enricher;
    }

    // TODO: Make this general on base reifier so all EIPs with agg strategy can use this
    private AggregationStrategy createAggregationStrategy() {
        AggregationStrategy strategy = definition.getAggregationStrategyBean();
        String ref = parseString(definition.getAggregationStrategy());
        if (strategy == null && ref != null) {
            Object aggStrategy = lookup(ref, Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy) aggStrategy;
            } else if (aggStrategy instanceof BiFunction) {
                AggregationStrategyBiFunctionAdapter adapter
                        = new AggregationStrategyBiFunctionAdapter((BiFunction) aggStrategy);
                if (definition.getAggregationStrategyMethodName() != null) {
                    adapter.setAllowNullNewExchange(parseBoolean(definition.getAggregationStrategyMethodAllowNull(), false));
                    adapter.setAllowNullOldExchange(parseBoolean(definition.getAggregationStrategyMethodAllowNull(), false));
                }
                strategy = adapter;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(
                        aggStrategy, parseString(definition.getAggregationStrategyMethodName()));
                if (definition.getAggregationStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(parseBoolean(definition.getAggregationStrategyMethodAllowNull(), false));
                    adapter.setAllowNullOldExchange(parseBoolean(definition.getAggregationStrategyMethodAllowNull(), false));
                }
                strategy = adapter;
            } else {
                throw new IllegalArgumentException(
                        "Cannot find AggregationStrategy in Registry with name: " + definition.getAggregationStrategy());
            }
        }

        CamelContextAware.trySetCamelContext(strategy, camelContext);
        return strategy;
    }

}
