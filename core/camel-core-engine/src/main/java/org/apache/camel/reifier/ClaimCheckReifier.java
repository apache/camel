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
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ClaimCheckDefinition;
import org.apache.camel.model.ClaimCheckOperation;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.ClaimCheckProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.support.ObjectHelper;

import static org.apache.camel.util.ObjectHelper.notNull;

public class ClaimCheckReifier extends ProcessorReifier<ClaimCheckDefinition> {

    public ClaimCheckReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, ClaimCheckDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        notNull(definition.getOperation(), "operation", this);

        ClaimCheckProcessor claim = new ClaimCheckProcessor();
        claim.setOperation(parse(ClaimCheckOperation.class, definition.getOperation()).name());
        claim.setKey(parseString(definition.getKey()));
        claim.setFilter(parseString(definition.getFilter()));

        AggregationStrategy strategy = createAggregationStrategy();
        if (strategy != null) {
            claim.setAggregationStrategy(strategy);
        }

        // only filter or aggregation strategy can be configured not both
        String filter = parseString(definition.getFilter());
        if (filter != null && strategy != null) {
            throw new IllegalArgumentException("Cannot use both filter and custom aggregation strategy on ClaimCheck EIP");
        }

        // validate filter, we cannot have both +/- at the same time
        if (filter != null) {
            Iterable it = ObjectHelper.createIterable(filter, ",");
            boolean includeBody = false;
            boolean excludeBody = false;
            for (Object o : it) {
                String pattern = o.toString();
                if ("body".equals(pattern) || "+body".equals(pattern)) {
                    includeBody = true;
                } else if ("-body".equals(pattern)) {
                    excludeBody = true;
                }
            }
            if (includeBody && excludeBody) {
                throw new IllegalArgumentException("Cannot have both include and exclude body at the same time in the filter: " + definition.getFilter());
            }
            boolean includeHeaders = false;
            boolean excludeHeaders = false;
            for (Object o : it) {
                String pattern = o.toString();
                if ("headers".equals(pattern) || "+headers".equals(pattern)) {
                    includeHeaders = true;
                } else if ("-headers".equals(pattern)) {
                    excludeHeaders = true;
                }
            }
            if (includeHeaders && excludeHeaders) {
                throw new IllegalArgumentException("Cannot have both include and exclude headers at the same time in the filter: " + definition.getFilter());
            }
            boolean includeHeader = false;
            boolean excludeHeader = false;
            for (Object o : it) {
                String pattern = o.toString();
                if (pattern.startsWith("header:") || pattern.startsWith("+header:")) {
                    includeHeader = true;
                } else if (pattern.startsWith("-header:")) {
                    excludeHeader = true;
                }
            }
            if (includeHeader && excludeHeader) {
                throw new IllegalArgumentException("Cannot have both include and exclude header at the same time in the filter: " + definition.getFilter());
            }
        }

        return claim;
    }

    private AggregationStrategy createAggregationStrategy() {
        AggregationStrategy strategy = definition.getAggregationStrategy();
        if (strategy == null && definition.getAggregationStrategyRef() != null) {
            Object aggStrategy = lookup(parseString(definition.getAggregationStrategyRef()), Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy)aggStrategy;
            } else if (aggStrategy != null) {
                strategy = new AggregationStrategyBeanAdapter(aggStrategy, definition.getAggregationStrategyMethodName());
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
