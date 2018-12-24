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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.processor.EvaluateExpressionProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.support.CamelContextHelper;

class RecipientListReifier extends ProcessorReifier<RecipientListDefinition<?>> {

    RecipientListReifier(ProcessorDefinition<?> definition) {
        super((RecipientListDefinition<?>) definition);
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        final Expression expression = definition.getExpression().createExpression(routeContext);

        boolean isParallelProcessing = definition.getParallelProcessing() != null && definition.getParallelProcessing();
        boolean isStreaming = definition.getStreaming() != null && definition.getStreaming();
        boolean isParallelAggregate = definition.getParallelAggregate() != null && definition.getParallelAggregate();
        boolean isShareUnitOfWork = definition.getShareUnitOfWork() != null && definition.getShareUnitOfWork();
        boolean isStopOnException = definition.getStopOnException() != null && definition.getStopOnException();
        boolean isIgnoreInvalidEndpoints = definition.getIgnoreInvalidEndpoints() != null && definition.getIgnoreInvalidEndpoints();
        boolean isStopOnAggregateException = definition.getStopOnAggregateException() != null && definition.getStopOnAggregateException();

        RecipientList answer;
        if (definition.getDelimiter() != null) {
            answer = new RecipientList(routeContext.getCamelContext(), expression, definition.getDelimiter());
        } else {
            answer = new RecipientList(routeContext.getCamelContext(), expression);
        }
        answer.setAggregationStrategy(createAggregationStrategy(routeContext));
        answer.setParallelProcessing(isParallelProcessing);
        answer.setParallelAggregate(isParallelAggregate);
        answer.setStreaming(isStreaming);
        answer.setShareUnitOfWork(isShareUnitOfWork);
        answer.setStopOnException(isStopOnException);
        answer.setIgnoreInvalidEndpoints(isIgnoreInvalidEndpoints);
        answer.setStopOnAggregateException(isStopOnAggregateException);
        if (definition.getCacheSize() != null) {
            answer.setCacheSize(definition.getCacheSize());
        }
        if (definition.getOnPrepareRef() != null) {
            definition.setOnPrepare(CamelContextHelper.mandatoryLookup(routeContext.getCamelContext(), definition.getOnPrepareRef(), Processor.class));
        }
        if (definition.getOnPrepare() != null) {
            answer.setOnPrepare(definition.getOnPrepare());
        }
        if (definition.getTimeout() != null) {
            answer.setTimeout(definition.getTimeout());
        }

        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, definition, isParallelProcessing);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "RecipientList", definition, isParallelProcessing);
        answer.setExecutorService(threadPool);
        answer.setShutdownExecutorService(shutdownThreadPool);
        long timeout = definition.getTimeout() != null ? definition.getTimeout() : 0;
        if (timeout > 0 && !isParallelProcessing) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled.");
        }

        // create a pipeline with two processors
        // the first is the eval processor which evaluates the expression to use
        // the second is the recipient list
        List<Processor> pipe = new ArrayList<>(2);

        // the eval processor must be wrapped in error handler, so in case there was an
        // error during evaluation, the error handler can deal with it
        // the recipient list is not in error handler, as its has its own special error handling
        // when sending to the recipients individually
        Processor evalProcessor = new EvaluateExpressionProcessor(expression);
        evalProcessor = super.wrapInErrorHandler(routeContext, evalProcessor);

        pipe.add(evalProcessor);
        pipe.add(answer);

        // wrap in nested pipeline so this appears as one processor
        // (threads definition does this as well)
        return new Pipeline(routeContext.getCamelContext(), pipe) {
            @Override
            public String toString() {
                return "RecipientList[" + expression + "]";
            }
        };
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

        if (strategy == null) {
            // default to use latest aggregation strategy
            strategy = new UseLatestAggregationStrategy();
        }

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        if (definition.getShareUnitOfWork() != null && definition.getShareUnitOfWork()) {
            // wrap strategy in share unit of work
            strategy = new ShareUnitOfWorkAggregationStrategy(strategy);
        }

        return strategy;
    }

}
