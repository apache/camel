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
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.SynchronousExecutorService;

class AggregateReifier extends ProcessorReifier<AggregateDefinition> {

    AggregateReifier(ProcessorDefinition<?> definition) {
        super(AggregateDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        return createAggregator(routeContext);
    }

    protected AggregateProcessor createAggregator(RouteContext routeContext) throws Exception {
        Processor childProcessor = this.createChildProcessor(routeContext, true);

        // wrap the aggregate route in a unit of work processor
        CamelInternalProcessor internal = new CamelInternalProcessor(childProcessor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(routeContext));

        Expression correlation = definition.getExpression().createExpression(routeContext);
        AggregationStrategy strategy = createAggregationStrategy(routeContext);

        boolean parallel = definition.getParallelProcessing() != null && definition.getParallelProcessing();
        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, definition, parallel);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, "Aggregator", definition, parallel);
        if (threadPool == null && !parallel) {
            // executor service is mandatory for the Aggregator
            // we do not run in parallel mode, but use a synchronous executor, so we run in current thread
            threadPool = new SynchronousExecutorService();
            shutdownThreadPool = true;
        }

        AggregateProcessor answer = new AggregateProcessor(routeContext.getCamelContext(), internal,
                correlation, strategy, threadPool, shutdownThreadPool);

        AggregationRepository repository = createAggregationRepository(routeContext);
        if (repository != null) {
            answer.setAggregationRepository(repository);
        }

        if (definition.getAggregateController() == null && definition.getAggregateControllerRef() != null) {
            definition.setAggregateController(routeContext.mandatoryLookup(definition.getAggregateControllerRef(), AggregateController.class));
        }

        // this EIP supports using a shared timeout checker thread pool or fallback to create a new thread pool
        boolean shutdownTimeoutThreadPool = false;
        ScheduledExecutorService timeoutThreadPool = definition.getTimeoutCheckerExecutorService();
        if (timeoutThreadPool == null && definition.getTimeoutCheckerExecutorServiceRef() != null) {
            // lookup existing thread pool
            timeoutThreadPool = routeContext.getCamelContext().getRegistry().lookupByNameAndType(definition.getTimeoutCheckerExecutorServiceRef(), ScheduledExecutorService.class);
            if (timeoutThreadPool == null) {
                // then create a thread pool assuming the ref is a thread pool profile id
                timeoutThreadPool = routeContext.getCamelContext().getExecutorServiceManager().newScheduledThreadPool(this,
                        AggregateProcessor.AGGREGATE_TIMEOUT_CHECKER, definition.getTimeoutCheckerExecutorServiceRef());
                if (timeoutThreadPool == null) {
                    throw new IllegalArgumentException("ExecutorServiceRef " + definition.getTimeoutCheckerExecutorServiceRef()
                            + " not found in registry (as an ScheduledExecutorService instance) or as a thread pool profile.");
                }
                shutdownTimeoutThreadPool = true;
            }
        }
        answer.setTimeoutCheckerExecutorService(timeoutThreadPool);
        answer.setShutdownTimeoutCheckerExecutorService(shutdownTimeoutThreadPool);

        // set other options
        answer.setParallelProcessing(parallel);
        if (definition.getOptimisticLocking() != null) {
            answer.setOptimisticLocking(definition.getOptimisticLocking());
        }
        if (definition.getCompletionPredicate() != null) {
            Predicate predicate = definition.getCompletionPredicate().createPredicate(routeContext);
            answer.setCompletionPredicate(predicate);
        } else if (strategy instanceof Predicate) {
            // if aggregation strategy implements predicate and was not configured then use as fallback
            log.debug("Using AggregationStrategy as completion predicate: {}", strategy);
            answer.setCompletionPredicate((Predicate) strategy);
        }
        if (definition.getCompletionTimeoutExpression() != null) {
            Expression expression = definition.getCompletionTimeoutExpression().createExpression(routeContext);
            answer.setCompletionTimeoutExpression(expression);
        }
        if (definition.getCompletionTimeout() != null) {
            answer.setCompletionTimeout(definition.getCompletionTimeout());
        }
        if (definition.getCompletionInterval() != null) {
            answer.setCompletionInterval(definition.getCompletionInterval());
        }
        if (definition.getCompletionSizeExpression() != null) {
            Expression expression = definition.getCompletionSizeExpression().createExpression(routeContext);
            answer.setCompletionSizeExpression(expression);
        }
        if (definition.getCompletionSize() != null) {
            answer.setCompletionSize(definition.getCompletionSize());
        }
        if (definition.getCompletionFromBatchConsumer() != null) {
            answer.setCompletionFromBatchConsumer(definition.getCompletionFromBatchConsumer());
        }
        if (definition.getCompletionOnNewCorrelationGroup() != null) {
            answer.setCompletionOnNewCorrelationGroup(definition.getCompletionOnNewCorrelationGroup());
        }
        if (definition.getEagerCheckCompletion() != null) {
            answer.setEagerCheckCompletion(definition.getEagerCheckCompletion());
        }
        if (definition.getIgnoreInvalidCorrelationKeys() != null) {
            answer.setIgnoreInvalidCorrelationKeys(definition.getIgnoreInvalidCorrelationKeys());
        }
        if (definition.getCloseCorrelationKeyOnCompletion() != null) {
            answer.setCloseCorrelationKeyOnCompletion(definition.getCloseCorrelationKeyOnCompletion());
        }
        if (definition.getDiscardOnCompletionTimeout() != null) {
            answer.setDiscardOnCompletionTimeout(definition.getDiscardOnCompletionTimeout());
        }
        if (definition.getForceCompletionOnStop() != null) {
            answer.setForceCompletionOnStop(definition.getForceCompletionOnStop());
        }
        if (definition.getCompleteAllOnStop() != null) {
            answer.setCompleteAllOnStop(definition.getCompleteAllOnStop());
        }
        if (definition.getOptimisticLockRetryPolicy() == null) {
            if (definition.getOptimisticLockRetryPolicyDefinition() != null) {
                answer.setOptimisticLockRetryPolicy(definition.getOptimisticLockRetryPolicyDefinition().createOptimisticLockRetryPolicy());
            }
        } else {
            answer.setOptimisticLockRetryPolicy(definition.getOptimisticLockRetryPolicy());
        }
        if (definition.getAggregateController() != null) {
            answer.setAggregateController(definition.getAggregateController());
        }
        if (definition.getCompletionTimeoutCheckerInterval() != null) {
            answer.setCompletionTimeoutCheckerInterval(definition.getCompletionTimeoutCheckerInterval());
        }
        return answer;
    }

    private AggregationStrategy createAggregationStrategy(RouteContext routeContext) {
        AggregationStrategy strategy = definition.getAggregationStrategy();
        if (strategy == null && definition.getStrategyRef() != null) {
            Object aggStrategy = routeContext.lookup(definition.getStrategyRef(), Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy) aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, definition.getAggregationStrategyMethodName());
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
            throw new IllegalArgumentException("AggregationStrategy or AggregationStrategyRef must be set on " + this);
        }

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware) strategy).setCamelContext(routeContext.getCamelContext());
        }

        return strategy;
    }

    private AggregationRepository createAggregationRepository(RouteContext routeContext) {
        AggregationRepository repository = definition.getAggregationRepository();
        if (repository == null && definition.getAggregationRepositoryRef() != null) {
            repository = routeContext.mandatoryLookup(definition.getAggregationRepositoryRef(), AggregationRepository.class);
        }
        return repository;
    }

}
