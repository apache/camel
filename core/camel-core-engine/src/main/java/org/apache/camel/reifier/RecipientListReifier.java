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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.processor.EvaluateExpressionProcessor;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;

public class RecipientListReifier extends ProcessorReifier<RecipientListDefinition<?>> {

    public RecipientListReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (RecipientListDefinition<?>)definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        final Expression expression = createExpression(definition.getExpression());

        boolean isParallelProcessing = parseBoolean(definition.getParallelProcessing(), false);
        boolean isStreaming = parseBoolean(definition.getStreaming(), false);
        boolean isParallelAggregate = parseBoolean(definition.getParallelAggregate(), false);
        boolean isShareUnitOfWork = parseBoolean(definition.getShareUnitOfWork(), false);
        boolean isStopOnException = parseBoolean(definition.getStopOnException(), false);
        boolean isIgnoreInvalidEndpoints = parseBoolean(definition.getIgnoreInvalidEndpoints(), false);
        boolean isStopOnAggregateException = parseBoolean(definition.getStopOnAggregateException(), false);

        RecipientList answer;
        if (definition.getDelimiter() != null) {
            answer = new RecipientList(camelContext, expression, parseString(definition.getDelimiter()));
        } else {
            answer = new RecipientList(camelContext, expression);
        }
        answer.setAggregationStrategy(createAggregationStrategy());
        answer.setParallelProcessing(isParallelProcessing);
        answer.setParallelAggregate(isParallelAggregate);
        answer.setStreaming(isStreaming);
        answer.setShareUnitOfWork(isShareUnitOfWork);
        answer.setStopOnException(isStopOnException);
        answer.setIgnoreInvalidEndpoints(isIgnoreInvalidEndpoints);
        answer.setStopOnAggregateException(isStopOnAggregateException);
        if (definition.getCacheSize() != null) {
            answer.setCacheSize(parseInt(definition.getCacheSize()));
        }
        if (definition.getOnPrepareRef() != null) {
            definition.setOnPrepare(mandatoryLookup(definition.getOnPrepareRef(), Processor.class));
        }
        if (definition.getOnPrepare() != null) {
            answer.setOnPrepare(definition.getOnPrepare());
        }
        if (definition.getTimeout() != null) {
            answer.setTimeout(parseLong(definition.getTimeout()));
        }

        boolean shutdownThreadPool = willCreateNewThreadPool(definition, isParallelProcessing);
        ExecutorService threadPool = getConfiguredExecutorService("RecipientList", definition, isParallelProcessing);
        answer.setExecutorService(threadPool);
        answer.setShutdownExecutorService(shutdownThreadPool);
        long timeout = definition.getTimeout() != null ? parseLong(definition.getTimeout()) : 0;
        if (timeout > 0 && !isParallelProcessing) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled.");
        }

        // create a pipeline with two processors
        // the first is the eval processor which evaluates the expression to use
        // the second is the recipient list
        List<Processor> pipe = new ArrayList<>(2);

        // the eval processor must be wrapped in error handler, so in case there
        // was an
        // error during evaluation, the error handler can deal with it
        // the recipient list is not in error handler, as its has its own
        // special error handling
        // when sending to the recipients individually
        Processor evalProcessor = new EvaluateExpressionProcessor(expression);
        evalProcessor = wrapInErrorHandler(evalProcessor, true);

        pipe.add(evalProcessor);
        pipe.add(answer);

        // wrap recipient list in nested pipeline so this appears as one processor
        return answer.newPipeline(camelContext, pipe);
    }

    private AggregationStrategy createAggregationStrategy() {
        AggregationStrategy strategy = definition.getAggregationStrategy();
        if (strategy == null && definition.getStrategyRef() != null) {
            Object aggStrategy = lookup(parseString(definition.getStrategyRef()), Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy)aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, parseString(definition.getStrategyMethodName()));
                if (definition.getStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(parseBoolean(definition.getStrategyMethodAllowNull(), false));
                    adapter.setAllowNullOldExchange(parseBoolean(definition.getStrategyMethodAllowNull(), false));
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
            ((CamelContextAware)strategy).setCamelContext(camelContext);
        }

        if (parseBoolean(definition.getShareUnitOfWork(), false)) {
            // wrap strategy in share unit of work
            strategy = new ShareUnitOfWorkAggregationStrategy(strategy);
        }

        return strategy;
    }

}
