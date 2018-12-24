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

import java.util.concurrent.ExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;

class SplitReifier extends ExpressionReifier<SplitDefinition> {

    SplitReifier(ProcessorDefinition<?> definition) {
        super((SplitDefinition) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);
        definition.setAggregationStrategy(createAggregationStrategy(routeContext));

        boolean isParallelProcessing = definition.getParallelProcessing() != null && definition.getParallelProcessing();
        boolean isStreaming = definition.getStreaming() != null && definition.getStreaming();
        boolean isShareUnitOfWork = definition.getShareUnitOfWork() != null && definition.getShareUnitOfWork();
        boolean isParallelAggregate = definition.getParallelAggregate() != null && definition.getParallelAggregate();
        boolean isStopOnAggregateException = definition.getStopOnAggregateException() != null && definition.getStopOnAggregateException();
        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, definition, isParallelProcessing);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "Split", definition, isParallelProcessing);

        long timeout = definition.getTimeout() != null ? definition.getTimeout() : 0;
        if (timeout > 0 && !isParallelProcessing) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled.");
        }
        if (definition.getOnPrepareRef() != null) {
            definition.setOnPrepare(CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), definition.getOnPrepareRef(), Processor.class));
        }

        Expression exp = definition.getExpression().createExpression(routeContext);

        Splitter answer = new Splitter(routeContext.getCamelContext(), exp, childProcessor, definition.getAggregationStrategy(),
                isParallelProcessing, threadPool, shutdownThreadPool, isStreaming, definition.isStopOnException(),
                timeout, definition.getOnPrepare(), isShareUnitOfWork, isParallelAggregate, isStopOnAggregateException);
        return answer;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = definition.getAggregationStrategy();
        if (strategy == null && definition.getStrategyRef() != null) {
            Object aggStrategy = routeContext.lookup(definition.getStrategyRef(), Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy) aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, definition.getStrategyMethodName());
                if (definition.getStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(definition.getStrategyMethodAllowNull());
                    adapter.setAllowNullOldExchange(definition.getStrategyMethodAllowNull());
                }
                strategy = adapter;
            } else {
                throw new IllegalArgumentException("Cannot find AggregationStrategy in Registry with name: " + definition.getStrategyRef());
            }
        }

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        if (strategy != null && definition.getShareUnitOfWork() != null && definition.getShareUnitOfWork()) {
            // wrap strategy in share unit of work
            strategy = new ShareUnitOfWorkAggregationStrategy(strategy);
        }

        return strategy;
    }

}
