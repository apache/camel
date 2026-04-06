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

import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.AggregationStrategyBiFunctionAdapter;
import org.apache.camel.processor.aggregate.ShareUnitOfWorkAggregationStrategy;
import org.apache.camel.resume.ResumeStrategy;

public class SplitReifier extends ExpressionReifier<SplitDefinition> {

    public SplitReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (SplitDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        Processor childProcessor = this.createChildProcessor(true);

        final AggregationStrategy strategy = createAggregationStrategy();

        boolean isParallelProcessing = parseBoolean(definition.getParallelProcessing(), false);
        boolean isSynchronous = parseBoolean(definition.getSynchronous(), false);
        boolean isStreaming = parseBoolean(definition.getStreaming(), false);
        boolean isShareUnitOfWork = parseBoolean(definition.getShareUnitOfWork(), false);
        boolean isParallelAggregate = parseBoolean(definition.getParallelAggregate(), false);
        boolean isStopOnException = parseBoolean(definition.getStopOnException(), false);
        boolean shutdownThreadPool = willCreateNewThreadPool(definition, isParallelProcessing);
        ExecutorService threadPool = getConfiguredExecutorService("Split", definition, isParallelProcessing);

        long timeout = parseDuration(definition.getTimeout(), 0);
        if (timeout > 0 && !isParallelProcessing) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled.");
        }
        Processor prepare = definition.getOnPrepareProcessor();
        if (prepare == null && definition.getOnPrepare() != null) {
            prepare = mandatoryLookup(definition.getOnPrepare(), Processor.class);
        }

        Expression exp = createExpression(definition.getExpression());
        String delimiter = parseString(definition.getDelimiter());

        Splitter answer;
        if (delimiter != null) {
            answer = new Splitter(
                    camelContext, route, exp, childProcessor, strategy, isParallelProcessing,
                    threadPool, shutdownThreadPool, isStreaming, isStopOnException, timeout, prepare,
                    isShareUnitOfWork, isParallelAggregate, delimiter);
        } else {
            answer = new Splitter(
                    camelContext, route, exp, childProcessor, strategy, isParallelProcessing,
                    threadPool, shutdownThreadPool, isStreaming, isStopOnException, timeout, prepare,
                    isShareUnitOfWork, isParallelAggregate);
        }
        answer.setSynchronous(isSynchronous);
        answer.setDisabled(isDisabled(camelContext, definition));

        int group = parseInt(definition.getGroup(), 0);
        if (group > 0) {
            answer.setGroup(group);
        }

        configureErrorThreshold(answer, isStopOnException);
        configureWatermark(answer);

        return answer;
    }

    private void configureErrorThreshold(Splitter answer, boolean isStopOnException) {
        String etStr = parseString(definition.getErrorThreshold());
        double errorThreshold = etStr != null ? Double.parseDouble(etStr) : 0;
        int maxFailedRecords = parseInt(definition.getMaxFailedRecords(), 0);
        boolean hasErrorThreshold = errorThreshold > 0 || maxFailedRecords > 0;
        if (hasErrorThreshold && isStopOnException) {
            throw new IllegalArgumentException(
                    "Cannot use both stopOnException and errorThreshold/maxFailedRecords on the Splitter EIP");
        }
        if (errorThreshold != 0 && (errorThreshold < 0 || errorThreshold > 1.0)) {
            throw new IllegalArgumentException(
                    "errorThreshold must be between 0.0 and 1.0, but was: " + errorThreshold);
        }
        if (maxFailedRecords < 0) {
            throw new IllegalArgumentException(
                    "maxFailedRecords must not be negative, but was: " + maxFailedRecords);
        }
        if (errorThreshold > 0) {
            answer.setErrorThreshold(errorThreshold);
        }
        if (maxFailedRecords > 0) {
            answer.setMaxFailedRecords(maxFailedRecords);
        }
    }

    private void configureWatermark(Splitter answer) {
        ResumeStrategy resumeStrategy = definition.getResumeStrategyBean();
        if (resumeStrategy == null && definition.getResumeStrategy() != null) {
            resumeStrategy = mandatoryLookup(definition.getResumeStrategy(), ResumeStrategy.class);
        }
        if (resumeStrategy != null) {
            CamelContextAware.trySetCamelContext(resumeStrategy, camelContext);
        }
        String watermarkKey = parseString(definition.getWatermarkKey());
        String watermarkExprStr = parseString(definition.getWatermarkExpression());
        if ((resumeStrategy != null) != (watermarkKey != null)) {
            throw new IllegalArgumentException(
                    "Both resumeStrategy and watermarkKey must be configured together on the Splitter EIP");
        }
        if (watermarkExprStr != null && resumeStrategy == null) {
            throw new IllegalArgumentException(
                    "watermarkExpression requires resumeStrategy and watermarkKey on the Splitter EIP");
        }
        if (resumeStrategy != null && watermarkKey != null) {
            answer.setResumeStrategy(resumeStrategy);
            answer.setWatermarkKey(watermarkKey);
            if (watermarkExprStr != null) {
                Expression watermarkExpr = camelContext.resolveLanguage("simple").createExpression(watermarkExprStr);
                answer.setWatermarkExpression(watermarkExpr);
            }
        }
    }

    private AggregationStrategy createAggregationStrategy() {
        AggregationStrategy strategy = definition.getAggregationStrategyBean();
        if (strategy == null && definition.getAggregationStrategy() != null) {
            Object aggStrategy = lookupByName(definition.getAggregationStrategy());
            if (aggStrategy == null) {
                aggStrategy = lookupByNameAndType(definition.getAggregationStrategy(), AggregationStrategy.class);
            }
            if (aggStrategy instanceof AggregationStrategy aggregationStrategy) {
                strategy = aggregationStrategy;
            } else if (aggStrategy instanceof BiFunction biFunction) {
                AggregationStrategyBiFunctionAdapter adapter
                        = new AggregationStrategyBiFunctionAdapter(biFunction);
                if (definition.getAggregationStrategyMethodAllowNull() != null) {
                    adapter.setAllowNullNewExchange(parseBoolean(definition.getAggregationStrategyMethodAllowNull(), false));
                    adapter.setAllowNullOldExchange(parseBoolean(definition.getAggregationStrategyMethodAllowNull(), false));
                }
                strategy = adapter;
            } else if (aggStrategy != null) {
                @SuppressWarnings("resource")
                // NOTE: the adapter holds no leaking resources, so we can safely ignore its closure.
                AggregationStrategyBeanAdapter adapter
                        = new AggregationStrategyBeanAdapter(aggStrategy, definition.getAggregationStrategyMethodName());
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
        if (strategy != null && parseBoolean(definition.getShareUnitOfWork(), false)) {
            // wrap strategy in share unit of work
            strategy = new ShareUnitOfWorkAggregationStrategy(strategy);
        }

        return strategy;
    }

}
