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
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.OptimisticLockRetryPolicyDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.processor.aggregate.OptimisticLockRetryPolicy;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.util.concurrent.SynchronousExecutorService;

public class AggregateReifier extends ProcessorReifier<AggregateDefinition> {

    public AggregateReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, AggregateDefinition.class.cast(definition));
    }

    @Override
    public Processor createProcessor() throws Exception {
        return createAggregator();
    }

    protected AggregateProcessor createAggregator() throws Exception {
        Processor childProcessor = this.createChildProcessor(true);

        // wrap the aggregate route in a unit of work processor
        CamelInternalProcessor internal = new CamelInternalProcessor(camelContext, childProcessor);
        internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(route, camelContext));

        Expression correlation = createExpression(definition.getExpression());
        AggregationStrategy strategy = createAggregationStrategy();

        boolean parallel = parseBoolean(definition.getParallelProcessing(), false);
        boolean shutdownThreadPool = willCreateNewThreadPool(definition, parallel);
        ExecutorService threadPool = getConfiguredExecutorService("Aggregator", definition, parallel);
        if (threadPool == null && !parallel) {
            // executor service is mandatory for the Aggregator
            // we do not run in parallel mode, but use a synchronous executor,
            // so we run in current thread
            threadPool = new SynchronousExecutorService();
            shutdownThreadPool = true;
        }

        AggregateProcessor answer = new AggregateProcessor(camelContext, internal, correlation, strategy, threadPool, shutdownThreadPool);

        AggregationRepository repository = createAggregationRepository();
        if (repository != null) {
            answer.setAggregationRepository(repository);
        }

        if (definition.getAggregateController() == null && definition.getAggregateControllerRef() != null) {
            definition.setAggregateController(mandatoryLookup(definition.getAggregateControllerRef(), AggregateController.class));
        }

        // this EIP supports using a shared timeout checker thread pool or
        // fallback to create a new thread pool
        boolean shutdownTimeoutThreadPool = false;
        ScheduledExecutorService timeoutThreadPool = definition.getTimeoutCheckerExecutorService();
        if (timeoutThreadPool == null && definition.getTimeoutCheckerExecutorServiceRef() != null) {
            // lookup existing thread pool
            timeoutThreadPool = lookup(definition.getTimeoutCheckerExecutorServiceRef(), ScheduledExecutorService.class);
            if (timeoutThreadPool == null) {
                // then create a thread pool assuming the ref is a thread pool
                // profile id
                timeoutThreadPool = camelContext.getExecutorServiceManager().newScheduledThreadPool(this, AggregateProcessor.AGGREGATE_TIMEOUT_CHECKER,
                                                                                                                      definition.getTimeoutCheckerExecutorServiceRef());
                if (timeoutThreadPool == null) {
                    throw new IllegalArgumentException("ExecutorServiceRef " + definition.getTimeoutCheckerExecutorServiceRef()
                                                       + " not found in registry (as an ScheduledExecutorService instance) or as a thread pool profile.");
                }
                shutdownTimeoutThreadPool = true;
            }
        }
        answer.setTimeoutCheckerExecutorService(timeoutThreadPool);
        answer.setShutdownTimeoutCheckerExecutorService(shutdownTimeoutThreadPool);

        if (parseBoolean(definition.getCompletionFromBatchConsumer(), false)
                && parseBoolean(definition.getDiscardOnAggregationFailure(), false)) {
            throw new IllegalArgumentException("Cannot use both completionFromBatchConsumer and discardOnAggregationFailure on: " + definition);
        }

        // set other options
        answer.setParallelProcessing(parallel);
        Boolean optimisticLocking = parseBoolean(definition.getOptimisticLocking());
        if (optimisticLocking != null) {
            answer.setOptimisticLocking(optimisticLocking);
        }
        if (definition.getCompletionPredicate() != null) {
            Predicate predicate = createPredicate(definition.getCompletionPredicate());
            answer.setCompletionPredicate(predicate);
        } else if (strategy instanceof Predicate) {
            // if aggregation strategy implements predicate and was not
            // configured then use as fallback
            log.debug("Using AggregationStrategy as completion predicate: {}", strategy);
            answer.setCompletionPredicate((Predicate)strategy);
        }
        if (definition.getCompletionTimeoutExpression() != null) {
            Expression expression = createExpression(definition.getCompletionTimeoutExpression());
            answer.setCompletionTimeoutExpression(expression);
        }
        Long completionTimeout = parseLong(definition.getCompletionTimeout());
        if (completionTimeout != null) {
            answer.setCompletionTimeout(completionTimeout);
        }
        Long completionInterval = parseLong(definition.getCompletionInterval());
        if (completionInterval != null) {
            answer.setCompletionInterval(completionInterval);
        }
        if (definition.getCompletionSizeExpression() != null) {
            Expression expression = createExpression(definition.getCompletionSizeExpression());
            answer.setCompletionSizeExpression(expression);
        }
        Integer completionSize = parseInt(definition.getCompletionSize());
        if (completionSize != null) {
            answer.setCompletionSize(completionSize);
        }
        Boolean completionFromBatchConsumer = parseBoolean(definition.getCompletionFromBatchConsumer());
        if (completionFromBatchConsumer != null) {
            answer.setCompletionFromBatchConsumer(completionFromBatchConsumer);
        }
        Boolean completionOnNewCorrelationGroup = parseBoolean(definition.getCompletionOnNewCorrelationGroup());
        if (completionOnNewCorrelationGroup != null) {
            answer.setCompletionOnNewCorrelationGroup(completionOnNewCorrelationGroup);
        }
        Boolean eagerCheckCompletion = parseBoolean(definition.getEagerCheckCompletion());
        if (eagerCheckCompletion != null) {
            answer.setEagerCheckCompletion(eagerCheckCompletion);
        }
        Boolean ignoreInvalidCorrelationKeys = parseBoolean(definition.getIgnoreInvalidCorrelationKeys());
        if (ignoreInvalidCorrelationKeys != null) {
            answer.setIgnoreInvalidCorrelationKeys(ignoreInvalidCorrelationKeys);
        }
        Integer closeCorrelationKeyOnCompletion = parseInt(definition.getCloseCorrelationKeyOnCompletion());
        if (closeCorrelationKeyOnCompletion != null) {
            answer.setCloseCorrelationKeyOnCompletion(closeCorrelationKeyOnCompletion);
        }
        Boolean discardOnCompletionTimeout = parseBoolean(definition.getDiscardOnCompletionTimeout());
        if (discardOnCompletionTimeout != null) {
            answer.setDiscardOnCompletionTimeout(discardOnCompletionTimeout);
        }
        Boolean discardOnAggregationFailure = parseBoolean(definition.getDiscardOnAggregationFailure());
        if (discardOnAggregationFailure != null) {
            answer.setDiscardOnAggregationFailure(discardOnAggregationFailure);
        }
        Boolean forceCompletionOnStop = parseBoolean(definition.getForceCompletionOnStop());
        if (forceCompletionOnStop != null) {
            answer.setForceCompletionOnStop(forceCompletionOnStop);
        }
        Boolean completeAllOnStop = parseBoolean(definition.getCompleteAllOnStop());
        if (completeAllOnStop != null) {
            answer.setCompleteAllOnStop(completeAllOnStop);
        }
        if (definition.getOptimisticLockRetryPolicy() == null) {
            if (definition.getOptimisticLockRetryPolicyDefinition() != null) {
                answer.setOptimisticLockRetryPolicy(createOptimisticLockRetryPolicy(definition.getOptimisticLockRetryPolicyDefinition()));
            }
        } else {
            answer.setOptimisticLockRetryPolicy(definition.getOptimisticLockRetryPolicy());
        }
        if (definition.getAggregateController() != null) {
            answer.setAggregateController(definition.getAggregateController());
        }
        Long completionTimeoutCheckerInterval = parseLong(definition.getCompletionTimeoutCheckerInterval());
        if (completionTimeoutCheckerInterval != null) {
            answer.setCompletionTimeoutCheckerInterval(completionTimeoutCheckerInterval);
        }
        return answer;
    }

    public OptimisticLockRetryPolicy createOptimisticLockRetryPolicy(OptimisticLockRetryPolicyDefinition definition) {
        OptimisticLockRetryPolicy policy = new OptimisticLockRetryPolicy();
        if (definition.getMaximumRetries() != null) {
            policy.setMaximumRetries(parseInt(definition.getMaximumRetries()));
        }
        if (definition.getRetryDelay() != null) {
            policy.setRetryDelay(parseLong(definition.getRetryDelay()));
        }
        if (definition.getMaximumRetryDelay() != null) {
            policy.setMaximumRetryDelay(parseLong(definition.getMaximumRetryDelay()));
        }
        if (definition.getExponentialBackOff() != null) {
            policy.setExponentialBackOff(parseBoolean(definition.getExponentialBackOff(), false));
        }
        if (definition.getRandomBackOff() != null) {
            policy.setRandomBackOff(parseBoolean(definition.getRandomBackOff(), false));
        }
        return policy;
    }

    private AggregationStrategy createAggregationStrategy() {
        AggregationStrategy strategy = definition.getAggregationStrategy();
        if (strategy == null && definition.getStrategyRef() != null) {
            Object aggStrategy = lookup(definition.getStrategyRef(), Object.class);
            if (aggStrategy instanceof AggregationStrategy) {
                strategy = (AggregationStrategy)aggStrategy;
            } else if (aggStrategy != null) {
                AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(aggStrategy, definition.getAggregationStrategyMethodName());
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
            throw new IllegalArgumentException("AggregationStrategy or AggregationStrategyRef must be set on " + this);
        }

        if (strategy instanceof CamelContextAware) {
            ((CamelContextAware)strategy).setCamelContext(camelContext);
        }

        return strategy;
    }

    private AggregationRepository createAggregationRepository() {
        AggregationRepository repository = definition.getAggregationRepository();
        if (repository == null && definition.getAggregationRepositoryRef() != null) {
            repository = mandatoryLookup(definition.getAggregationRepositoryRef(), AggregationRepository.class);
        }
        return repository;
    }

}
