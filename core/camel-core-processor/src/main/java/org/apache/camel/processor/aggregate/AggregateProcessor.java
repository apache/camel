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
package org.apache.camel.processor.aggregate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.TimeoutMap;
import org.apache.camel.Traceable;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.OptimisticLockingAggregationRepository;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.ShutdownPrepared;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultTimeoutMap;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.support.NoLock;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the <a href="http://camel.apache.org/aggregator2.html">Aggregator</a> pattern where a batch of
 * messages is processed (up to a maximum amount or until some timeout is reached) and messages for the same correlation
 * key are combined using some kind of {@link AggregationStrategy} (by default the latest message is used) to compress
 * many message exchanges into a smaller number of exchanges.
 * <p/>
 * A good example of this is stock market data; you may be receiving 30,000 messages/second, and you may want to
 * throttle it right down so that multiple messages for the same stock are combined (or just the latest message is used
 * and older prices to be discarded). Another idea is to combine line item messages together into a single invoice
 * message.
 */
public class AggregateProcessor extends AsyncProcessorSupport
        implements Navigate<Processor>, Traceable, ShutdownPrepared, ShutdownAware, IdAware, RouteIdAware {

    public static final String AGGREGATE_TIMEOUT_CHECKER = "AggregateTimeoutChecker";
    public static final String AGGREGATE_OPTIMISTIC_LOCKING_EXECUTOR = "AggregateOptimisticLockingExecutor";

    public static final String COMPLETED_BY_SIZE = "size";
    public static final String COMPLETED_BY_PREDICATE = "predicate";
    public static final String COMPLETED_BY_CONSUMER = "consumer";
    public static final String COMPLETED_BY_STRATEGY = "strategy";
    public static final String COMPLETED_BY_INTERVAL = "interval";
    public static final String COMPLETED_BY_TIMEOUT = "timeout";
    public static final String COMPLETED_BY_FORCE = "force";

    private static final Logger LOG = LoggerFactory.getLogger(AggregateProcessor.class);
    private volatile Lock lock;
    private final AtomicBoolean aggregateRepositoryWarned = new AtomicBoolean();
    private final CamelContext camelContext;
    private final ReactiveExecutor reactiveExecutor;
    private final AsyncProcessor processor;
    private String id;
    private String routeId;
    private AggregationStrategy aggregationStrategy;
    private boolean preCompletion;
    private Expression correlationExpression;
    private AggregateController aggregateController;
    private final ExecutorService executorService;
    private final boolean shutdownExecutorService;
    private OptimisticLockRetryPolicy optimisticLockRetryPolicy = new OptimisticLockRetryPolicy();
    private ScheduledExecutorService timeoutCheckerExecutorService;
    private boolean shutdownTimeoutCheckerExecutorService;
    private ScheduledExecutorService optimisticLockingExecutorService;
    private boolean shutdownOptimisticLockingExecutorService;
    private ScheduledExecutorService recoverService;
    // store correlation key -> exchange id in timeout map
    private TimeoutMap<String, String> timeoutMap;
    private ExceptionHandler exceptionHandler;
    private AggregationRepository aggregationRepository;
    private Map<String, String> closedCorrelationKeys;
    private final Set<String> batchConsumerCorrelationKeys = new ConcurrentSkipListSet<>();
    private final Set<String> inProgressCompleteExchanges = ConcurrentHashMap.newKeySet();
    private final Set<String> unconfirmedCompleteExchanges = ConcurrentHashMap.newKeySet();
    private final Set<String> inProgressCompleteExchangesForRecoveryTask = ConcurrentHashMap.newKeySet();
    private final Map<String, RedeliveryData> redeliveryState = new ConcurrentHashMap<>();

    private final AggregateProcessorStatistics statistics = new Statistics();
    private final AtomicLong totalIn = new AtomicLong();
    private final AtomicLong totalCompleted = new AtomicLong();
    private final AtomicLong completedBySize = new AtomicLong();
    private final AtomicLong completedByStrategy = new AtomicLong();
    private final AtomicLong completedByInterval = new AtomicLong();
    private final AtomicLong completedByTimeout = new AtomicLong();
    private final AtomicLong completedByPredicate = new AtomicLong();
    private final AtomicLong completedByBatchConsumer = new AtomicLong();
    private final AtomicLong completedByForce = new AtomicLong();
    private final AtomicLong discarded = new AtomicLong();
    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);

    // keep booking about redelivery
    private static class RedeliveryData {
        int redeliveryCounter;
    }

    private class Statistics implements AggregateProcessorStatistics {

        private boolean statisticsEnabled = true;

        @Override
        public long getTotalIn() {
            return totalIn.get();
        }

        @Override
        public long getTotalCompleted() {
            return totalCompleted.get();
        }

        @Override
        public long getCompletedBySize() {
            return completedBySize.get();
        }

        @Override
        public long getCompletedByStrategy() {
            return completedByStrategy.get();
        }

        @Override
        public long getCompletedByInterval() {
            return completedByInterval.get();
        }

        @Override
        public long getCompletedByTimeout() {
            return completedByTimeout.get();
        }

        @Override
        public long getCompletedByPredicate() {
            return completedByPredicate.get();
        }

        @Override
        public long getCompletedByBatchConsumer() {
            return completedByBatchConsumer.get();
        }

        @Override
        public long getCompletedByForce() {
            return completedByForce.get();
        }

        @Override
        public long getDiscarded() {
            return discarded.get();
        }

        @Override
        public void reset() {
            totalIn.set(0);
            totalCompleted.set(0);
            completedBySize.set(0);
            completedByStrategy.set(0);
            completedByTimeout.set(0);
            completedByPredicate.set(0);
            completedByBatchConsumer.set(0);
            completedByForce.set(0);
            discarded.set(0);
        }

        @Override
        public boolean isStatisticsEnabled() {
            return statisticsEnabled;
        }

        @Override
        public void setStatisticsEnabled(boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
        }
    }

    // options
    private boolean ignoreInvalidCorrelationKeys;
    private Integer closeCorrelationKeyOnCompletion;
    private boolean parallelProcessing;
    private boolean optimisticLocking;

    // different ways to have completion triggered
    private boolean eagerCheckCompletion;
    private Predicate completionPredicate;
    private long completionTimeout;
    private Expression completionTimeoutExpression;
    private long completionInterval;
    private int completionSize;
    private Expression completionSizeExpression;
    private boolean completionFromBatchConsumer;
    private boolean completionOnNewCorrelationGroup;
    private final AtomicInteger batchConsumerCounter = new AtomicInteger();
    private boolean discardOnCompletionTimeout;
    private boolean discardOnAggregationFailure;
    private boolean forceCompletionOnStop;
    private boolean completeAllOnStop;
    private long completionTimeoutCheckerInterval = 1000;

    private ProducerTemplate deadLetterProducerTemplate;
    private boolean isRecoverableRepository;

    public AggregateProcessor(CamelContext camelContext, AsyncProcessor processor,
                              Expression correlationExpression, AggregationStrategy aggregationStrategy,
                              ExecutorService executorService, boolean shutdownExecutorService) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(processor, "processor");
        ObjectHelper.notNull(correlationExpression, "correlationExpression");
        ObjectHelper.notNull(aggregationStrategy, "aggregationStrategy");
        ObjectHelper.notNull(executorService, "executorService");
        this.camelContext = camelContext;
        this.reactiveExecutor = camelContext.getCamelContextExtension().getReactiveExecutor();
        this.processor = processor;
        this.correlationExpression = correlationExpression;
        this.aggregationStrategy = aggregationStrategy;
        this.executorService = executorService;
        this.shutdownExecutorService = shutdownExecutorService;
        this.exceptionHandler = new LoggingExceptionHandler(camelContext, getClass());
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "aggregate[" + correlationExpression + "]";
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<>(1);
        answer.add(processor);
        return answer;
    }

    @Override
    public boolean hasNext() {
        return processor != null;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            return doProcess(exchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    protected boolean doProcess(Exchange exchange, AsyncCallback callback) throws Exception {
        if (getStatistics().isStatisticsEnabled()) {
            totalIn.incrementAndGet();
        }

        // check for the special header to force completion of all groups (and ignore the exchange otherwise)
        if (isCompleteAllGroups(exchange)) {
            removeFlagCompleteAllGroups(exchange);
            forceCompletionOfAllGroups();
            callback.done(true);
            return true;
        }

        // compute correlation expression
        String key = correlationExpression.evaluate(exchange, String.class);
        if (ObjectHelper.isEmpty(key)) {
            // we have a bad correlation key
            if (isIgnoreInvalidCorrelationKeys()) {
                LOG.debug("Invalid correlation key. This Exchange will be ignored: {}", exchange);
            } else {
                exchange.setException(new CamelExchangeException("Invalid correlation key", exchange));
            }
            callback.done(true);
            return true;
        }

        // is the correlation key closed?
        if (closedCorrelationKeys != null && closedCorrelationKeys.containsKey(key)) {
            exchange.setException(new ClosedCorrelationKeyException(key, exchange));
            callback.done(true);
            return true;
        }

        if (optimisticLocking) {
            return doInOptimisticLock(exchange, key, callback, 0, true);
        } else {
            return doProcess(exchange, key, callback, true);
        }
    }

    protected boolean doInOptimisticLock(Exchange exchange, String key, AsyncCallback callback, int attempt, boolean sync) {
        while (true) {
            attempt++;
            try {
                return doProcess(exchange, key, callback, sync);
            } catch (OptimisticLockingAggregationRepository.OptimisticLockingException e) {
                LOG.trace(
                        "On attempt {} OptimisticLockingAggregationRepository: {} threw OptimisticLockingException while trying to aggregate exchange: {}",
                        attempt, aggregationRepository, exchange, e);
                if (optimisticLockRetryPolicy.shouldRetry(attempt)) {
                    long delay = optimisticLockRetryPolicy.getDelay(attempt);
                    if (delay > 0) {
                        int nextAttempt = attempt;
                        getOptimisticLockingExecutorService().schedule(
                                () -> doInOptimisticLock(exchange, key, callback, nextAttempt, false), delay,
                                TimeUnit.MILLISECONDS);
                        return false;
                    }
                } else {
                    exchange.setException(new CamelExchangeException(
                            "Exhausted optimistic locking retry attempts, tried " + attempt + " times", exchange,
                            new OptimisticLockingAggregationRepository.OptimisticLockingException()));
                    callback.done(sync);
                    return sync;
                }
            }
        }
    }

    protected boolean doProcess(Exchange exchange, String key, AsyncCallback callback, boolean sync) {
        // copy exchange, and do not share the unit of work
        // the aggregated output runs in another unit of work
        Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);

        // remove the complete all groups flags as it should not be on the copy
        removeFlagCompleteCurrentGroup(copy);
        removeFlagCompleteAllGroups(copy);
        removeFlagCompleteAllGroupsInclusive(copy);

        List<Exchange> aggregated = null;
        lock.lock();
        try {
            aggregated = doAggregation(key, copy);
        } catch (CamelExchangeException e) {
            exchange.setException(e);
        } finally {
            lock.unlock();
        }

        // we are completed so do that work outside the lock
        if (aggregated != null) {
            // we are completed so submit to completion
            aggregated.forEach(agg -> onSubmitCompletion(key, agg));
        }

        // check for the special header to force completion of all groups (inclusive of the message)
        if (isCompleteAllGroupsInclusive(exchange)) {
            removeFlagCompleteAllGroupsInclusive(exchange);
            forceCompletionOfAllGroups();
        }

        callback.done(sync);
        return sync;
    }

    private Object removeFlagCompleteCurrentGroup(Exchange exchange) {
        return exchange.removeProperty(ExchangePropertyKey.AGGREGATION_COMPLETE_CURRENT_GROUP);
    }

    private Boolean isCompleteCurrentGroup(Exchange exchange) {
        return exchange.getProperty(ExchangePropertyKey.AGGREGATION_COMPLETE_CURRENT_GROUP, boolean.class);
    }

    private Object removeFlagCompleteAllGroups(Exchange exchange) {
        Object removedHeader = exchange.getIn().removeHeader(Exchange.AGGREGATION_COMPLETE_ALL_GROUPS);
        Object removedProp = exchange.removeProperty(ExchangePropertyKey.AGGREGATION_COMPLETE_ALL_GROUPS);
        return removedHeader == null ? removedProp : removedHeader;
    }

    private Boolean isCompleteAllGroups(Exchange exchange) {
        boolean retVal = exchange.getIn().getHeader(Exchange.AGGREGATION_COMPLETE_ALL_GROUPS, false, boolean.class);
        if (!retVal) {
            // according to doc it is a property but it is sometimes read as header
            // some test don't fail because they use the header expression which contains a fallback to properties
            retVal = exchange.getProperty(ExchangePropertyKey.AGGREGATION_COMPLETE_ALL_GROUPS, boolean.class);
        }
        return retVal;
    }

    private Object removeFlagCompleteAllGroupsInclusive(Exchange exchange) {
        return exchange.getIn().removeHeader(Exchange.AGGREGATION_COMPLETE_ALL_GROUPS_INCLUSIVE);
    }

    private Boolean isCompleteAllGroupsInclusive(Exchange exchange) {
        return exchange.getIn().getHeader(Exchange.AGGREGATION_COMPLETE_ALL_GROUPS_INCLUSIVE, false, boolean.class);
    }

    /**
     * Aggregates the exchange with the given correlation key
     * <p/>
     * This method <b>must</b> be run synchronized as we cannot aggregate the same correlation key in parallel.
     * <p/>
     * The returned {@link Exchange} should be send downstream using the
     * {@link #onSubmitCompletion(String, org.apache.camel.Exchange)} method which sends out the aggregated and
     * completed {@link Exchange}.
     *
     * @param  key                                     the correlation key
     * @param  newExchange                             the exchange
     * @return                                         the aggregated exchange(s) which is complete, or <tt>null</tt> if
     *                                                 not yet complete
     * @throws org.apache.camel.CamelExchangeException is thrown if error aggregating
     */
    private List<Exchange> doAggregation(String key, Exchange newExchange) throws CamelExchangeException {
        LOG.trace("onAggregation +++ start +++ with correlation key: {}", key);

        List<Exchange> list = new ArrayList<>();
        String complete = null;

        Exchange answer;
        Exchange originalExchange = aggregationRepository.get(newExchange.getContext(), key);
        Exchange oldExchange = originalExchange;

        Integer size = 1;
        if (oldExchange != null) {
            // hack to support legacy AggregationStrategy's that modify and return the oldExchange, these will not
            // working when using an identify based approach for optimistic locking like the MemoryAggregationRepository.
            if (optimisticLocking && aggregationRepository instanceof MemoryAggregationRepository) {
                oldExchange = originalExchange.copy();
            }
            size = oldExchange.getProperty(ExchangePropertyKey.AGGREGATED_SIZE, 0, Integer.class);
            size++;
        }

        // prepare the exchanges for aggregation
        ExchangeHelper.prepareAggregation(oldExchange, newExchange);

        // check if we are pre complete
        if (preCompletion) {
            try {
                // put the current aggregated size on the exchange so its avail during completion check
                newExchange.setProperty(ExchangePropertyKey.AGGREGATED_SIZE, size);
                newExchange.setProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY, key);
                complete = isPreCompleted(key, oldExchange, newExchange);
                // make sure to track timeouts if not complete
                if (complete == null) {
                    trackTimeout(key, newExchange);
                }
                // remove it afterwards
                newExchange.removeProperty(ExchangePropertyKey.AGGREGATED_SIZE);
                newExchange.removeProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY);
            } catch (Exception e) {
                // must catch any exception from aggregation
                throw new CamelExchangeException("Error occurred during preComplete", newExchange, e);
            }
        } else if (isEagerCheckCompletion()) {
            // put the current aggregated size on the exchange so its avail during completion check
            newExchange.setProperty(ExchangePropertyKey.AGGREGATED_SIZE, size);
            newExchange.setProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY, key);
            complete = isCompleted(key, newExchange);
            // make sure to track timeouts if not complete
            if (complete == null) {
                trackTimeout(key, newExchange);
            }
            // remove it afterwards
            newExchange.removeProperty(ExchangePropertyKey.AGGREGATED_SIZE);
            newExchange.removeProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY);
        }

        if (preCompletion && complete != null) {
            // need to pre complete the current group before we aggregate
            doAggregationComplete(complete, list, key, originalExchange, oldExchange, false);
            // as we complete the current group eager, we should indicate the new group is not complete
            complete = null;
            // and clear old/original exchange as we start on a new group
            oldExchange = null;
            originalExchange = null;
            // and reset the size to 1
            size = 1;
            // make sure to track timeout as we just restart the correlation group when we are in pre completion mode
            trackTimeout(key, newExchange);
        }

        // aggregate the exchanges
        boolean aggregateFailed = false;
        try {
            answer = onAggregation(oldExchange, newExchange);
        } catch (Exception e) {
            aggregateFailed = true;
            if (isDiscardOnAggregationFailure()) {
                // discard due failure in aggregation strategy
                LOG.debug(
                        "Aggregation for correlation key {} discarding aggregated exchange: {} due to failure in AggregationStrategy caused by: {}",
                        key, oldExchange, e.getMessage());
                complete = COMPLETED_BY_STRATEGY;
                answer = oldExchange;
                if (answer == null) {
                    // first message in group failed during aggregation and we should just discard this
                    return null;
                }
            } else {
                // must catch any exception from aggregation
                throw new CamelExchangeException("Error occurred during aggregation", newExchange, e);
            }
        }
        if (answer == null) {
            throw new CamelExchangeException(
                    "AggregationStrategy " + aggregationStrategy + " returned null which is not allowed", newExchange);
        }

        // check for the special exchange property to force completion of all groups
        if (isCompleteAllGroups(answer)) {
            removeFlagCompleteAllGroups(answer);
            forceCompletionOfAllGroups();
        } else if (isCompletionOnNewCorrelationGroup() && originalExchange == null) {
            // its a new group so force complete of all existing groups
            forceCompletionOfAllGroups();
        }

        // special for some repository implementations
        if (isRecoverableRepository()) {
            boolean valid = oldExchange == null || answer.getExchangeId().equals(oldExchange.getExchangeId());
            if (!valid && aggregateRepositoryWarned.compareAndSet(false, true)) {
                LOG.warn(
                        "AggregationStrategy should return the oldExchange instance instead of the newExchange whenever possible"
                         + " as otherwise this can lead to unexpected behavior with some RecoverableAggregationRepository implementations");
            }
        }

        // update the aggregated size
        answer.setProperty(ExchangePropertyKey.AGGREGATED_SIZE, size);

        // maybe we should check completion after the aggregation
        if (!preCompletion && !isEagerCheckCompletion()) {
            complete = isCompleted(key, answer);
            // make sure to track timeouts if not complete
            if (complete == null) {
                trackTimeout(key, newExchange);
            }
        }

        if (!aggregateFailed && complete == null) {
            // only need to update aggregation repository if we are not complete
            doAggregationRepositoryAdd(newExchange.getContext(), key, originalExchange, answer);
        } else {
            // if we are complete then add the answer to the list
            doAggregationComplete(complete, list, key, originalExchange, answer, aggregateFailed);
        }

        LOG.trace("onAggregation +++  end  +++ with correlation key: {}", key);
        return list;
    }

    protected void doAggregationComplete(
            String complete, List<Exchange> list, String key,
            Exchange originalExchange, Exchange answer, boolean aggregateFailed) {
        if (COMPLETED_BY_CONSUMER.equals(complete)) {
            for (String batchKey : batchConsumerCorrelationKeys) {
                Exchange batchAnswer;
                Exchange batchOriginalExchange;
                if (batchKey.equals(key)) {
                    // skip the current aggregated key as we have already aggregated it and have the answer
                    batchAnswer = answer;
                    batchOriginalExchange = originalExchange;
                } else {
                    batchAnswer = aggregationRepository.get(camelContext, batchKey);
                    batchOriginalExchange = batchAnswer;
                }

                if (batchAnswer != null) {
                    batchAnswer.setProperty(ExchangePropertyKey.AGGREGATED_COMPLETED_BY, complete);
                    onCompletion(batchKey, batchOriginalExchange, batchAnswer, false, aggregateFailed);
                    list.add(batchAnswer);
                }
            }
            batchConsumerCorrelationKeys.clear();
            // we have already submitted to completion, so answer should be null
            answer = null;
        } else if (answer != null) {
            // we are complete for this exchange
            answer.setProperty(ExchangePropertyKey.AGGREGATED_COMPLETED_BY, complete);
            answer = onCompletion(key, originalExchange, answer, false, aggregateFailed);
        }

        if (answer != null) {
            list.add(answer);
        }
    }

    protected void doAggregationRepositoryAdd(
            CamelContext camelContext, String key, Exchange oldExchange, Exchange newExchange) {
        LOG.trace("In progress aggregated oldExchange: {}, newExchange: {} with correlation key: {}", oldExchange, newExchange,
                key);
        if (optimisticLocking) {
            try {
                ((OptimisticLockingAggregationRepository) aggregationRepository).add(camelContext, key, oldExchange,
                        newExchange);
            } catch (OptimisticLockingAggregationRepository.OptimisticLockingException e) {
                onOptimisticLockingFailure(oldExchange, newExchange);
                throw e;
            }
        } else {
            aggregationRepository.add(camelContext, key, newExchange);
        }
    }

    protected void onOptimisticLockingFailure(Exchange oldExchange, Exchange newExchange) {
        aggregationStrategy.onOptimisticLockFailure(oldExchange, newExchange);
    }

    /**
     * Tests whether the given exchanges is pre-complete or not
     *
     * @param  key         the correlation key
     * @param  oldExchange the existing exchange
     * @param  newExchange the incoming exchange
     * @return             <tt>null</tt> if not pre-completed, otherwise a String with the type that triggered the
     *                     pre-completion
     */
    protected String isPreCompleted(String key, Exchange oldExchange, Exchange newExchange) {
        return aggregationStrategy.preComplete(oldExchange, newExchange) ? COMPLETED_BY_STRATEGY : null;
    }

    /**
     * Tests whether the given exchange is complete or not
     *
     * @param  key      the correlation key
     * @param  exchange the incoming exchange
     * @return          <tt>null</tt> if not completed, otherwise a String with the type that triggered the completion
     */
    protected String isCompleted(String key, Exchange exchange) {
        // batch consumer completion must always run first
        if (isCompletionFromBatchConsumer()) {
            batchConsumerCorrelationKeys.add(key);
            batchConsumerCounter.incrementAndGet();
            int size = exchange.getProperty(ExchangePropertyKey.BATCH_SIZE, 0, Integer.class);
            if (size > 0 && batchConsumerCounter.intValue() >= size) {
                // batch consumer is complete then reset the counter
                batchConsumerCounter.set(0);
                return COMPLETED_BY_CONSUMER;
            }
        }

        if (isCompleteCurrentGroup(exchange)) {
            removeFlagCompleteCurrentGroup(exchange);
            return COMPLETED_BY_STRATEGY;
        }

        if (getCompletionPredicate() != null) {
            boolean answer = getCompletionPredicate().matches(exchange);
            if (answer) {
                return COMPLETED_BY_PREDICATE;
            }
        }

        boolean sizeChecked = false;
        if (getCompletionSizeExpression() != null) {
            Integer value = getCompletionSizeExpression().evaluate(exchange, Integer.class);
            if (value != null && value > 0) {
                // mark as already checked size as expression takes precedence over static configured
                sizeChecked = true;
                int size = exchange.getProperty(ExchangePropertyKey.AGGREGATED_SIZE, 1, Integer.class);
                if (size >= value) {
                    return COMPLETED_BY_SIZE;
                }
            }
        }
        if (!sizeChecked && getCompletionSize() > 0) {
            int size = exchange.getProperty(ExchangePropertyKey.AGGREGATED_SIZE, 1, Integer.class);
            if (size >= getCompletionSize()) {
                return COMPLETED_BY_SIZE;
            }
        }

        // not complete
        return null;
    }

    protected void trackTimeout(String key, Exchange exchange) {
        // timeout can be either evaluated based on an expression or from a fixed value
        // expression takes precedence
        boolean timeoutSet = false;
        if (getCompletionTimeoutExpression() != null) {
            Long value = getCompletionTimeoutExpression().evaluate(exchange, Long.class);
            if (value != null && value > 0) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Updating correlation key {} to timeout after {} ms. as exchange received: {}",
                            key, value, exchange);
                }
                addExchangeToTimeoutMap(key, exchange, value);
                timeoutSet = true;
            }
        }
        if (!timeoutSet && getCompletionTimeout() > 0) {
            // timeout is used so use the timeout map to keep an eye on this
            if (LOG.isTraceEnabled()) {
                LOG.trace("Updating correlation key {} to timeout after {} ms. as exchange received: {}",
                        key, getCompletionTimeout(), exchange);
            }
            addExchangeToTimeoutMap(key, exchange, getCompletionTimeout());
        }
    }

    protected Exchange onAggregation(Exchange oldExchange, Exchange newExchange) {
        return aggregationStrategy.aggregate(oldExchange, newExchange);
    }

    protected Exchange onCompletion(
            final String key, final Exchange original, final Exchange aggregated, boolean fromTimeout,
            boolean aggregateFailed) {
        // store the correlation key as property before we remove so the repository has that information
        if (original != null) {
            original.setProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY, key);
        }
        aggregated.setProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY, key);

        // only remove if we have previous added (as we could potentially complete with only 1 exchange)
        // (if we have previous added then we have that as the original exchange)
        if (original != null) {
            // remove from repository as its completed, we do this first as to trigger any OptimisticLockingException's
            aggregationRepository.remove(aggregated.getContext(), key, original);
        }

        if (!fromTimeout && timeoutMap != null) {
            // cleanup timeout map if it was a incoming exchange which triggered the timeout (and not the timeout checker)
            LOG.trace("Removing correlation key {} from timeout", key);
            timeoutMap.remove(key);
        }

        // this key has been closed so add it to the closed map
        if (closedCorrelationKeys != null) {
            closedCorrelationKeys.put(key, key);
        }

        if (fromTimeout) {
            // invoke timeout to allow any custom processing before discarding the exchange
            long timeout = getCompletionTimeout() > 0 ? getCompletionTimeout() : -1;
            aggregationStrategy.timeout(aggregated, -1, -1, timeout);
        }

        Exchange answer;
        if (fromTimeout && isDiscardOnCompletionTimeout()) {
            // this exchange is discarded
            discarded.incrementAndGet();
            // discard due timeout
            LOG.debug("Aggregation for correlation key {} discarding aggregated exchange: {}", key, aggregated);
            // must confirm the discarded exchange
            aggregationRepository.confirm(aggregated.getContext(), aggregated.getExchangeId());
            // and remove redelivery state as well
            redeliveryState.remove(aggregated.getExchangeId());
            // the completion was from timeout and we should just discard it
            answer = null;
        } else if (aggregateFailed && isDiscardOnAggregationFailure()) {
            // this exchange is discarded
            discarded.incrementAndGet();
            // discard due aggregation failed (or by force)
            LOG.debug("Aggregation for correlation key {} discarding aggregated exchange: {}", key, aggregated);
            // must confirm the discarded exchange
            aggregationRepository.confirm(aggregated.getContext(), aggregated.getExchangeId());
            // and remove redelivery state as well
            redeliveryState.remove(aggregated.getExchangeId());
            // the completion was failed during aggregation and we should just discard it
            answer = null;
        } else {
            // the aggregated exchange should be published (sent out)
            answer = aggregated;
        }

        return answer;
    }

    private void onSubmitCompletion(final String key, final Exchange exchange) {
        LOG.debug("Aggregation complete for correlation key {} sending aggregated exchange: {}", key, exchange);

        // add this as in progress before we submit the task
        inProgressCompleteExchanges.add(exchange.getExchangeId());
        if (recoveryInProgress.get()) {
            inProgressCompleteExchangesForRecoveryTask.add(exchange.getExchangeId());
        }
        // invoke the on completion callback
        aggregationStrategy.onCompletion(exchange);

        if (getStatistics().isStatisticsEnabled()) {
            totalCompleted.incrementAndGet();

            String completedBy = exchange.getProperty(ExchangePropertyKey.AGGREGATED_COMPLETED_BY, String.class);
            switch (completedBy) {
                case COMPLETED_BY_INTERVAL:
                    completedByInterval.incrementAndGet();
                    break;
                case COMPLETED_BY_TIMEOUT:
                    completedByTimeout.incrementAndGet();
                    break;
                case COMPLETED_BY_FORCE:
                    completedByForce.incrementAndGet();
                    break;
                case COMPLETED_BY_CONSUMER:
                    completedByBatchConsumer.incrementAndGet();
                    break;
                case COMPLETED_BY_PREDICATE:
                    completedByPredicate.incrementAndGet();
                    break;
                case COMPLETED_BY_SIZE:
                    completedBySize.incrementAndGet();
                    break;
                case COMPLETED_BY_STRATEGY:
                    completedByStrategy.incrementAndGet();
                    break;
                default:
                    LOG.error("Invalid value of {} property: {}", Exchange.AGGREGATED_COMPLETED_BY, exchange);
                    break;
            }
        }

        LOG.debug("Processing aggregated exchange: {}", exchange);

        // add on completion task so we remember to update the inProgressCompleteExchanges
        exchange.getExchangeExtension().addOnCompletion(new AggregateOnCompletion(exchange.getExchangeId()));

        // send this exchange
        executorService.execute(() -> {
            ExchangeHelper.prepareOutToIn(exchange);

            Runnable task = () -> processor.process(exchange, done -> {
                // log exception if there was a problem
                if (exchange.getException() != null) {
                    // if there was an exception then let the exception handler handle it
                    getExceptionHandler().handleException("Error processing aggregated exchange", exchange,
                            exchange.getException());
                } else {
                    LOG.trace("Processing aggregated exchange: {} complete.", exchange);
                }
            });
            // the call to schedule is needed to ensure in-order processing of the aggregates
            reactiveExecutor.schedule(task);
        });
    }

    /**
     * Restores the timeout map with timeout values from the aggregation repository.
     * <p/>
     * This is needed in case the aggregator has been stopped and started again (for example a server restart). Then the
     * existing exchanges from the {@link AggregationRepository} must have their timeout conditions restored.
     */
    protected void restoreTimeoutMapFromAggregationRepository() throws Exception {
        // grab the timeout value for each partly aggregated exchange
        Set<String> keys = aggregationRepository.getKeys();
        if (keys == null || keys.isEmpty()) {
            return;
        }

        StopWatch watch = new StopWatch();
        LOG.trace("Starting restoring CompletionTimeout for {} existing exchanges from the aggregation repository...",
                keys.size());

        for (String key : keys) {
            Exchange exchange = aggregationRepository.get(camelContext, key);
            if (exchange != null) {
                // grab the timeout value
                long timeout = exchange.getProperty(ExchangePropertyKey.AGGREGATED_TIMEOUT, 0L, long.class);
                if (timeout > 0) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Restoring CompletionTimeout for exchangeId: {} with timeout: {} millis.",
                                exchange.getExchangeId(), timeout);
                    }
                    addExchangeToTimeoutMap(key, exchange, timeout);
                }
            }
        }

        // log duration of this task so end user can see how long it takes to pre-check this upon starting
        LOG.info("Restored {} CompletionTimeout conditions in the AggregationTimeoutChecker in {}",
                timeoutMap.size(), TimeUtils.printDuration(watch.taken(), true));
    }

    /**
     * Adds the given exchange to the timeout map, which is used by the timeout checker task to trigger timeouts.
     *
     * @param key      the correlation key
     * @param exchange the exchange
     * @param timeout  the timeout value in millis
     */
    private void addExchangeToTimeoutMap(String key, Exchange exchange, long timeout) {
        // store the timeout value on the exchange as well, in case we need it later
        exchange.setProperty(ExchangePropertyKey.AGGREGATED_TIMEOUT, timeout);
        timeoutMap.put(key, exchange.getExchangeId(), timeout);
    }

    /**
     * Current number of closed correlation keys in the memory cache
     */
    public int getClosedCorrelationKeysCacheSize() {
        if (closedCorrelationKeys != null) {
            return closedCorrelationKeys.size();
        } else {
            return 0;
        }
    }

    /**
     * Clear all the closed correlation keys stored in the cache
     */
    public void clearClosedCorrelationKeysCache() {
        if (closedCorrelationKeys != null) {
            closedCorrelationKeys.clear();
        }
    }

    public AggregateProcessorStatistics getStatistics() {
        return statistics;
    }

    public int getInProgressCompleteExchanges() {
        return inProgressCompleteExchanges.size();
    }

    public Predicate getCompletionPredicate() {
        return completionPredicate;
    }

    public void setCompletionPredicate(Predicate completionPredicate) {
        this.completionPredicate = completionPredicate;
    }

    public boolean isEagerCheckCompletion() {
        return eagerCheckCompletion;
    }

    public void setEagerCheckCompletion(boolean eagerCheckCompletion) {
        this.eagerCheckCompletion = eagerCheckCompletion;
    }

    public long getCompletionTimeout() {
        return completionTimeout;
    }

    public void setCompletionTimeout(long completionTimeout) {
        this.completionTimeout = completionTimeout;
    }

    public Expression getCompletionTimeoutExpression() {
        return completionTimeoutExpression;
    }

    public void setCompletionTimeoutExpression(Expression completionTimeoutExpression) {
        this.completionTimeoutExpression = completionTimeoutExpression;
    }

    public long getCompletionInterval() {
        return completionInterval;
    }

    public void setCompletionInterval(long completionInterval) {
        this.completionInterval = completionInterval;
    }

    public int getCompletionSize() {
        return completionSize;
    }

    public void setCompletionSize(int completionSize) {
        this.completionSize = completionSize;
    }

    public Expression getCompletionSizeExpression() {
        return completionSizeExpression;
    }

    public void setCompletionSizeExpression(Expression completionSizeExpression) {
        this.completionSizeExpression = completionSizeExpression;
    }

    public boolean isIgnoreInvalidCorrelationKeys() {
        return ignoreInvalidCorrelationKeys;
    }

    public void setIgnoreInvalidCorrelationKeys(boolean ignoreInvalidCorrelationKeys) {
        this.ignoreInvalidCorrelationKeys = ignoreInvalidCorrelationKeys;
    }

    public Integer getCloseCorrelationKeyOnCompletion() {
        return closeCorrelationKeyOnCompletion;
    }

    public void setCloseCorrelationKeyOnCompletion(Integer closeCorrelationKeyOnCompletion) {
        this.closeCorrelationKeyOnCompletion = closeCorrelationKeyOnCompletion;
    }

    public boolean isCompletionFromBatchConsumer() {
        return completionFromBatchConsumer;
    }

    public void setCompletionFromBatchConsumer(boolean completionFromBatchConsumer) {
        this.completionFromBatchConsumer = completionFromBatchConsumer;
    }

    public boolean isCompletionOnNewCorrelationGroup() {
        return completionOnNewCorrelationGroup;
    }

    public void setCompletionOnNewCorrelationGroup(boolean completionOnNewCorrelationGroup) {
        this.completionOnNewCorrelationGroup = completionOnNewCorrelationGroup;
    }

    public boolean isCompleteAllOnStop() {
        return completeAllOnStop;
    }

    public long getCompletionTimeoutCheckerInterval() {
        return completionTimeoutCheckerInterval;
    }

    public void setCompletionTimeoutCheckerInterval(long completionTimeoutCheckerInterval) {
        this.completionTimeoutCheckerInterval = completionTimeoutCheckerInterval;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public void setParallelProcessing(boolean parallelProcessing) {
        this.parallelProcessing = parallelProcessing;
    }

    public boolean isOptimisticLocking() {
        return optimisticLocking;
    }

    public void setOptimisticLocking(boolean optimisticLocking) {
        this.optimisticLocking = optimisticLocking;
    }

    public AggregationRepository getAggregationRepository() {
        return aggregationRepository;
    }

    public void setAggregationRepository(AggregationRepository aggregationRepository) {
        this.aggregationRepository = aggregationRepository;
        this.isRecoverableRepository = aggregationRepository instanceof RecoverableAggregationRepository;
    }

    private boolean isRecoverableRepository() {
        return isRecoverableRepository;
    }

    public boolean isDiscardOnCompletionTimeout() {
        return discardOnCompletionTimeout;
    }

    public void setDiscardOnCompletionTimeout(boolean discardOnCompletionTimeout) {
        this.discardOnCompletionTimeout = discardOnCompletionTimeout;
    }

    public boolean isDiscardOnAggregationFailure() {
        return discardOnAggregationFailure;
    }

    public void setDiscardOnAggregationFailure(boolean discardOnAggregationFailure) {
        this.discardOnAggregationFailure = discardOnAggregationFailure;
    }

    public void setForceCompletionOnStop(boolean forceCompletionOnStop) {
        this.forceCompletionOnStop = forceCompletionOnStop;
    }

    public void setCompleteAllOnStop(boolean completeAllOnStop) {
        this.completeAllOnStop = completeAllOnStop;
    }

    public void setTimeoutCheckerExecutorService(ScheduledExecutorService timeoutCheckerExecutorService) {
        this.timeoutCheckerExecutorService = timeoutCheckerExecutorService;
    }

    public ScheduledExecutorService getTimeoutCheckerExecutorService() {
        return timeoutCheckerExecutorService;
    }

    public boolean isShutdownTimeoutCheckerExecutorService() {
        return shutdownTimeoutCheckerExecutorService;
    }

    public void setShutdownTimeoutCheckerExecutorService(boolean shutdownTimeoutCheckerExecutorService) {
        this.shutdownTimeoutCheckerExecutorService = shutdownTimeoutCheckerExecutorService;
    }

    public void setOptimisticLockingExecutorService(ScheduledExecutorService optimisticLockingExecutorService) {
        this.optimisticLockingExecutorService = optimisticLockingExecutorService;
    }

    public ScheduledExecutorService getOptimisticLockingExecutorService() {
        return optimisticLockingExecutorService;
    }

    public boolean isShutdownOptimisticLockingExecutorService() {
        return shutdownOptimisticLockingExecutorService;
    }

    public void setShutdownOptimisticLockingExecutorService(boolean shutdownOptimisticLockingExecutorService) {
        this.shutdownOptimisticLockingExecutorService = shutdownOptimisticLockingExecutorService;
    }

    public void setOptimisticLockRetryPolicy(OptimisticLockRetryPolicy optimisticLockRetryPolicy) {
        this.optimisticLockRetryPolicy = optimisticLockRetryPolicy;
    }

    public OptimisticLockRetryPolicy getOptimisticLockRetryPolicy() {
        return optimisticLockRetryPolicy;
    }

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    public Expression getCorrelationExpression() {
        return correlationExpression;
    }

    public void setCorrelationExpression(Expression correlationExpression) {
        this.correlationExpression = correlationExpression;
    }

    public AggregateController getAggregateController() {
        return aggregateController;
    }

    public void setAggregateController(AggregateController aggregateController) {
        this.aggregateController = aggregateController;
    }

    /**
     * On completion task which keeps the booking of the in progress up to date
     */
    private final class AggregateOnCompletion implements Synchronization {
        private final String exchangeId;

        private AggregateOnCompletion(String exchangeId) {
            // must use the original exchange id as it could potentially change if send over SEDA etc.
            this.exchangeId = exchangeId;
        }

        @Override
        public void onFailure(Exchange exchange) {
            LOG.trace("Aggregated exchange onFailure: {}", exchange);

            // must remember to remove in progress when we failed
            inProgressCompleteExchanges.remove(exchangeId);
            // do not remove redelivery state as we need it when we redeliver again later
        }

        @Override
        public void onComplete(Exchange exchange) {
            LOG.trace("Aggregated exchange onComplete: {}", exchange);

            // only confirm if we processed without a problem
            try {
                boolean confirmed;
                if (isRecoverableRepository()) {
                    confirmed = ((RecoverableAggregationRepository) aggregationRepository)
                            .confirmWithResult(exchange.getContext(), exchangeId);
                } else {
                    aggregationRepository.confirm(exchange.getContext(), exchangeId);
                    confirmed = true;
                }
                // and remove redelivery state as well
                redeliveryState.remove(exchangeId);
                if (!confirmed) {
                    unconfirmedCompleteExchanges.add(exchangeId);
                }
            } finally {
                // must remember to remove in progress when we are complete
                inProgressCompleteExchanges.remove(exchangeId);
            }
        }

        @Override
        public String toString() {
            return "AggregateOnCompletion";
        }
    }

    /**
     * Background task that looks for aggregated exchanges which is triggered by completion timeouts.
     */
    private final class AggregationTimeoutMap extends DefaultTimeoutMap<String, String> {

        private AggregationTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
            // do NOT use locking on the timeout map as this aggregator has its own shared lock we will use instead
            super(executor, requestMapPollTimeMillis, optimisticLocking);
            addListener(this::onEviction);
        }

        @Override
        protected void purge() {
            // wait for lock to be created
            if (lock != null) {
                // must acquire the shared aggregation lock to be able to purge
                lock.lock();
                try {
                    super.purge();
                } finally {
                    lock.unlock();
                }
            }
        }

        private void onEviction(Listener.Type type, String key, String exchangeId) {
            if (type != Listener.Type.Evict) {
                return;
            }
            log.debug("Completion timeout triggered for correlation key: {}", key);

            boolean inProgress = inProgressCompleteExchanges.contains(exchangeId);
            if (inProgress) {
                log.trace("Aggregated exchange with id: {} is already in progress.", exchangeId);
                return;
            }

            // get the aggregated exchange
            boolean evictionStolen = false;
            Exchange answer = aggregationRepository.get(camelContext, key);
            if (answer == null) {
                evictionStolen = true;
            } else {
                // indicate it was completed by timeout
                answer.setProperty(ExchangePropertyKey.AGGREGATED_COMPLETED_BY, COMPLETED_BY_TIMEOUT);
                try {
                    answer = onCompletion(key, answer, answer, true, false);
                    if (answer != null) {
                        onSubmitCompletion(key, answer);
                    }
                } catch (OptimisticLockingAggregationRepository.OptimisticLockingException e) {
                    evictionStolen = true;
                }
            }

            if (optimisticLocking && evictionStolen) {
                log.debug("Another Camel instance has already successfully correlated or processed this timeout eviction "
                          + "for exchange with id: {} and correlation id: {}",
                        exchangeId, key);
            }
        }
    }

    /**
     * Background task that triggers completion based on interval.
     */
    private final class AggregationIntervalTask implements Runnable {

        @Override
        public void run() {
            // only run if CamelContext has been fully started
            if (!camelContext.getStatus().isStarted()) {
                LOG.trace("Completion interval task cannot start due CamelContext({}) has not been started yet",
                        camelContext.getName());
                return;
            }

            LOG.trace("Starting completion interval task");

            // trigger completion for all in the repository
            Set<String> keys = aggregationRepository.getKeys();

            if (keys != null && !keys.isEmpty()) {
                // must acquire the shared aggregation lock to be able to trigger interval completion
                lock.lock();
                try {
                    for (String key : keys) {
                        boolean stolenInterval = false;
                        Exchange exchange = aggregationRepository.get(camelContext, key);
                        if (exchange == null) {
                            stolenInterval = true;
                        } else {
                            LOG.trace("Completion interval triggered for correlation key: {}", key);
                            // indicate it was completed by interval
                            exchange.setProperty(ExchangePropertyKey.AGGREGATED_COMPLETED_BY, COMPLETED_BY_INTERVAL);
                            try {
                                Exchange answer = onCompletion(key, exchange, exchange, false, false);
                                if (answer != null) {
                                    onSubmitCompletion(key, answer);
                                }
                            } catch (OptimisticLockingAggregationRepository.OptimisticLockingException e) {
                                stolenInterval = true;
                            }
                        }
                        if (optimisticLocking && stolenInterval) {
                            LOG.debug(
                                    "Another Camel instance has already processed this interval aggregation for exchange with correlation id: {}",
                                    key);
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }

            LOG.trace("Completion interval task complete");
        }
    }

    /**
     * Background task that looks for aggregated exchanges to recover.
     */
    private final class RecoverTask implements Runnable {
        private final RecoverableAggregationRepository recoverable;

        private RecoverTask(RecoverableAggregationRepository recoverable) {
            this.recoverable = recoverable;
        }

        @Override
        public void run() {
            // only run if CamelContext has been fully started
            if (!camelContext.getStatus().isStarted()) {
                LOG.trace("Recover check cannot start due CamelContext({}) has not been started yet", camelContext.getName());
                return;
            }
            LOG.trace("Starting recover check");
            try {
                // copy the current in progress before doing scan
                recoveryInProgress.set(true);
                inProgressCompleteExchangesForRecoveryTask.clear();
                inProgressCompleteExchangesForRecoveryTask.addAll(inProgressCompleteExchanges);
                // These are delivered but still in complete repository!
                inProgressCompleteExchangesForRecoveryTask.addAll(unconfirmedCompleteExchanges);
                final Set<String> exchangeIds = recoverable.scan(camelContext);
                for (String exchangeId : exchangeIds) {

                    // we may shutdown while doing recovery
                    if (!isRunAllowed()) {
                        LOG.info("We are shutting down so stop recovering");
                        return;
                    }
                    lock.lock();
                    try {
                        // consider in progress if it was in progress before we did the scan, or currently after we did the scan
                        // its safer to consider it in progress than risk duplicates due both in progress + recovered
                        final boolean inProgress = inProgressCompleteExchangesForRecoveryTask.contains(exchangeId);
                        if (inProgress) {
                            LOG.trace("Aggregated exchange with id: {} is already in progress.", exchangeId);
                            if (unconfirmedCompleteExchanges.contains(exchangeId)) {
                                retryConfirm(exchangeId);
                            }
                        } else {
                            LOG.debug("Loading aggregated exchange with id: {} to be recovered.", exchangeId);
                            Exchange exchange = recoverable.recover(camelContext, exchangeId);
                            if (exchange != null) {
                                // get the correlation key
                                String key = exchange.getProperty(ExchangePropertyKey.AGGREGATED_CORRELATION_KEY, String.class);
                                // and mark it as redelivered
                                exchange.getIn().setHeader(Exchange.REDELIVERED, Boolean.TRUE);

                                // get the current redelivery data
                                RedeliveryData data = redeliveryState.get(exchange.getExchangeId());

                                // if we are exhausted, then move to dead letter channel
                                if (data != null && recoverable.getMaximumRedeliveries() > 0
                                        && data.redeliveryCounter >= recoverable.getMaximumRedeliveries()) {
                                    LOG.warn("The recovered exchange is exhausted after {} attempts, will now be moved to "
                                             + "dead letter channel: {}",
                                            recoverable.getMaximumRedeliveries(), recoverable.getDeadLetterUri());

                                    // send to DLC
                                    try {
                                        // set redelivery counter
                                        exchange.getIn().setHeader(Exchange.REDELIVERY_COUNTER, data.redeliveryCounter);
                                        // and prepare for sending to DLC
                                        exchange.getExchangeExtension().setRedeliveryExhausted(false);
                                        exchange.setRollbackOnly(false);
                                        deadLetterProducerTemplate.send(recoverable.getDeadLetterUri(), exchange);
                                    } catch (Exception e) {
                                        exchange.setException(e);
                                    }

                                    // handle if failed
                                    if (exchange.getException() != null) {
                                        getExceptionHandler()
                                                .handleException("Failed to move recovered Exchange to dead letter channel: "
                                                                 + recoverable.getDeadLetterUri(),
                                                        exchange,
                                                        exchange.getException());
                                    } else {
                                        // it was ok, so confirm after it has been moved to dead letter channel, so we wont recover it again
                                        recoverable.confirm(camelContext, exchangeId);
                                    }
                                } else {
                                    // update current redelivery state
                                    if (data == null) {
                                        // create new data
                                        data = new RedeliveryData();
                                        redeliveryState.put(exchange.getExchangeId(), data);
                                    }
                                    data.redeliveryCounter++;

                                    // set redelivery counter
                                    exchange.getIn().setHeader(Exchange.REDELIVERY_COUNTER, data.redeliveryCounter);
                                    if (recoverable.getMaximumRedeliveries() > 0) {
                                        exchange.getIn().setHeader(Exchange.REDELIVERY_MAX_COUNTER,
                                                recoverable.getMaximumRedeliveries());
                                    }

                                    LOG.debug("Delivery attempt: {} to recover aggregated exchange with id: {}",
                                            data.redeliveryCounter, exchangeId);

                                    // not exhaust so resubmit the recovered exchange
                                    onSubmitCompletion(key, exchange);
                                }
                            }
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } finally {
                recoveryInProgress.set(false);
                inProgressCompleteExchangesForRecoveryTask.clear();
            }
            LOG.trace("Recover check complete");
        }

        private void retryConfirm(String exchangeId) {
            // Confirm that the exchange was processed
            if (recoverable.confirmWithResult(camelContext, exchangeId)) {
                unconfirmedCompleteExchanges.remove(exchangeId);
                LOG.debug("Removal of exchange {} confirmed.", exchangeId);
            } else {
                LOG.warn("Still unable to confirm removal of exchange {}.", exchangeId);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doStart() throws Exception {
        CamelContextAware.trySetCamelContext(aggregationStrategy, camelContext);
        if (aggregationStrategy.canPreComplete()) {
            preCompletion = true;
            LOG.info("PreCompletionAwareAggregationStrategy detected. Aggregator {} is in pre-completion mode.", getId());
        }

        if (!preCompletion) {
            // if not in pre completion mode then check we configured the completion required
            if (getCompletionTimeout() <= 0 && getCompletionInterval() <= 0 && getCompletionSize() <= 0
                    && getCompletionPredicate() == null
                    && !isCompletionFromBatchConsumer() && getCompletionTimeoutExpression() == null
                    && getCompletionSizeExpression() == null) {
                throw new IllegalStateException(
                        "At least one of the completions options"
                                                + " [completionTimeout, completionInterval, completionSize, completionPredicate, completionFromBatchConsumer] must be set");
            }
        }

        if (getCloseCorrelationKeyOnCompletion() != null) {
            if (getCloseCorrelationKeyOnCompletion() > 0) {
                LOG.info("Using ClosedCorrelationKeys with a LRUCache with a capacity of {}",
                        getCloseCorrelationKeyOnCompletion());
                closedCorrelationKeys = LRUCacheFactory.newLRUCache(getCloseCorrelationKeyOnCompletion());
            } else {
                LOG.info("Using ClosedCorrelationKeys with unbounded capacity");
                closedCorrelationKeys = new ConcurrentHashMap<>();
            }
        }

        if (aggregationRepository == null) {
            aggregationRepository = new MemoryAggregationRepository(optimisticLocking);
            LOG.info("Defaulting to MemoryAggregationRepository");
        }

        if (optimisticLocking) {
            if (!(aggregationRepository instanceof OptimisticLockingAggregationRepository)) {
                throw new IllegalArgumentException(
                        "Optimistic locking cannot be enabled without using an AggregationRepository that implements OptimisticLockingAggregationRepository");
            }
            LOG.info("Optimistic locking is enabled");
        }

        ServiceHelper.startService(aggregationStrategy, processor, aggregationRepository);

        // should we use recover checker
        if (isRecoverableRepository()) {
            RecoverableAggregationRepository recoverable = (RecoverableAggregationRepository) aggregationRepository;
            if (recoverable.isUseRecovery()) {
                long interval = recoverable.getRecoveryIntervalInMillis();
                if (interval <= 0) {
                    throw new IllegalArgumentException(
                            "AggregationRepository has recovery enabled and the RecoveryInterval option must be a positive number, was: "
                                                       + interval);
                }

                // create a background recover thread to check every interval
                recoverService
                        = camelContext.getExecutorServiceManager().newScheduledThreadPool(this, "AggregateRecoverChecker", 1);
                Runnable recoverTask = new RecoverTask(recoverable);
                LOG.info("Using RecoverableAggregationRepository by scheduling recover checker to run every {} millis.",
                        interval);
                // use fixed delay so there is X interval between each run
                recoverService.scheduleWithFixedDelay(recoverTask, 1000L, interval, TimeUnit.MILLISECONDS);

                if (recoverable.getDeadLetterUri() != null) {
                    int max = recoverable.getMaximumRedeliveries();
                    if (max <= 0) {
                        throw new IllegalArgumentException("Option maximumRedeliveries must be a positive number, was: " + max);
                    }
                    LOG.info("After {} failed redelivery attempts Exchanges will be moved to deadLetterUri: {}", max,
                            recoverable.getDeadLetterUri());

                    // dead letter uri must be a valid endpoint
                    Endpoint endpoint = camelContext.getEndpoint(recoverable.getDeadLetterUri());
                    if (endpoint == null) {
                        throw new NoSuchEndpointException(recoverable.getDeadLetterUri());
                    }
                    deadLetterProducerTemplate = camelContext.createProducerTemplate();
                }
            }
        }

        if (getCompletionInterval() > 0 && getCompletionTimeout() > 0) {
            throw new IllegalArgumentException("Only one of completionInterval or completionTimeout can be used, not both.");
        }
        if (getCompletionInterval() > 0) {
            LOG.info("Using CompletionInterval to run every {} millis.", getCompletionInterval());
            if (getTimeoutCheckerExecutorService() == null) {
                setTimeoutCheckerExecutorService(camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                        AGGREGATE_TIMEOUT_CHECKER));
                shutdownTimeoutCheckerExecutorService = true;
            }
            // trigger completion based on interval
            getTimeoutCheckerExecutorService().scheduleAtFixedRate(new AggregationIntervalTask(), getCompletionInterval(),
                    getCompletionInterval(), TimeUnit.MILLISECONDS);
        }

        // start timeout service if its in use
        if (getCompletionTimeout() > 0 || getCompletionTimeoutExpression() != null) {
            LOG.info("Using CompletionTimeout to trigger after {} millis of inactivity.", getCompletionTimeout());
            if (getTimeoutCheckerExecutorService() == null) {
                setTimeoutCheckerExecutorService(camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this,
                        AGGREGATE_TIMEOUT_CHECKER));
                shutdownTimeoutCheckerExecutorService = true;
            }
            // check for timed out aggregated messages once every second
            timeoutMap = new AggregationTimeoutMap(getTimeoutCheckerExecutorService(), getCompletionTimeoutCheckerInterval());
            // fill in existing timeout values from the aggregation repository, for example if a restart occurred, then we
            // need to re-establish the timeout map so timeout can trigger
            restoreTimeoutMapFromAggregationRepository();
            ServiceHelper.startService(timeoutMap);
        }

        if (aggregateController == null) {
            aggregateController = new DefaultAggregateController();
        }
        aggregateController.onStart(this);

        if (optimisticLocking) {
            lock = NoLock.INSTANCE;
            if (getOptimisticLockingExecutorService() == null) {
                setOptimisticLockingExecutorService(camelContext.getExecutorServiceManager()
                        .newSingleThreadScheduledExecutor(this, AGGREGATE_OPTIMISTIC_LOCKING_EXECUTOR));
                shutdownOptimisticLockingExecutorService = true;
            }
        } else {
            lock = new ReentrantLock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        // note: we cannot do doForceCompletionOnStop from this doStop method
        // as this is handled in the prepareShutdown method which is also invoked when stopping a route
        // and is better suited for preparing to shutdown than this doStop method is

        if (aggregateController != null) {
            aggregateController.onStop(this);
        }

        if (recoverService != null) {
            camelContext.getExecutorServiceManager().shutdown(recoverService);
        }

        if (shutdownTimeoutCheckerExecutorService && timeoutCheckerExecutorService != null) {
            camelContext.getExecutorServiceManager().shutdown(timeoutCheckerExecutorService);
            timeoutCheckerExecutorService = null;
            shutdownTimeoutCheckerExecutorService = false;
        }

        ServiceHelper.stopService(timeoutMap, processor, deadLetterProducerTemplate);

        if (closedCorrelationKeys != null) {
            // it may be a service so stop it as well
            ServiceHelper.stopService(closedCorrelationKeys);
            closedCorrelationKeys.clear();
        }
        batchConsumerCorrelationKeys.clear();
        redeliveryState.clear();
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // we are shutting down, so force completion if this option was enabled
        // but only do this when forced=false, as that is when we have chance to
        // send out new messages to be routed by Camel. When forced=true, then
        // we have to shutdown in a hurry
        if (!forced && forceCompletionOnStop) {
            doForceCompletionOnStop();
        }
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // not in use
        return true;
    }

    @Override
    public int getPendingExchangesSize() {
        if (completeAllOnStop) {
            // we want to regard all pending exchanges in the repo as inflight
            Set<String> keys = getAggregationRepository().getKeys();
            return keys != null ? keys.size() : 0;
        } else {
            return 0;
        }
    }

    private void doForceCompletionOnStop() {
        int expected = forceCompletionOfAllGroups();

        StopWatch watch = new StopWatch();
        while (!inProgressCompleteExchanges.isEmpty()) {
            LOG.trace("Waiting for {} inflight exchanges to complete", getInProgressCompleteExchanges());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // break out as we got interrupted such as the JVM terminating
                LOG.warn("Interrupted while waiting for {} inflight exchanges to complete.", getInProgressCompleteExchanges());
                break;
            }
        }

        if (expected > 0) {
            LOG.info("Forcing completion of all groups with {} exchanges completed in {}", expected,
                    TimeUtils.printDuration(watch.taken(), true));
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        // shutdown aggregation repository and the strategy
        ServiceHelper.stopAndShutdownServices(aggregationRepository, aggregationStrategy);

        // cleanup when shutting down
        inProgressCompleteExchanges.clear();

        if (shutdownExecutorService) {
            camelContext.getExecutorServiceManager().shutdownNow(executorService);
        }
        if (shutdownTimeoutCheckerExecutorService) {
            camelContext.getExecutorServiceManager().shutdownNow(timeoutCheckerExecutorService);
            timeoutCheckerExecutorService = null;
        }
        if (shutdownOptimisticLockingExecutorService) {
            camelContext.getExecutorServiceManager().shutdownNow(optimisticLockingExecutorService);
            optimisticLockingExecutorService = null;
        }

        super.doShutdown();
    }

    public int forceCompletionOfGroup(String key) {
        // must acquire the shared aggregation lock to be able to trigger force completion
        int total = 0;

        lock.lock();
        try {
            Exchange exchange = aggregationRepository.get(camelContext, key);
            if (exchange != null) {
                total = 1;
                LOG.trace("Force completion triggered for correlation key: {}", key);
                // indicate it was completed by a force completion request
                exchange.setProperty(ExchangePropertyKey.AGGREGATED_COMPLETED_BY, COMPLETED_BY_FORCE);
                Exchange answer = onCompletion(key, exchange, exchange, false, false);
                if (answer != null) {
                    onSubmitCompletion(key, answer);
                }
            }
        } finally {
            lock.unlock();
        }
        LOG.trace("Completed force completion of group {}", key);

        if (total > 0) {
            LOG.debug("Forcing completion of group {} with {} exchanges", key, total);
        }
        return total;
    }

    public int forceCompletionOfAllGroups() {

        // only run if CamelContext has been fully started or is stopping
        boolean allow = camelContext.getStatus().isStarted() || camelContext.getStatus().isStopping();
        if (!allow) {
            LOG.warn("Cannot start force completion of all groups because CamelContext({}) has not been started",
                    camelContext.getName());
            return 0;
        }

        LOG.trace("Starting force completion of all groups task");

        // trigger completion for all in the repository
        Set<String> keys = aggregationRepository.getKeys();

        int total = 0;
        if (keys != null && !keys.isEmpty()) {
            // must acquire the shared aggregation lock to be able to trigger force completion
            lock.lock();
            total = keys.size();
            try {
                for (String key : keys) {
                    Exchange exchange = aggregationRepository.get(camelContext, key);
                    if (exchange != null) {
                        LOG.trace("Force completion triggered for correlation key: {}", key);
                        // indicate it was completed by a force completion request
                        exchange.setProperty(ExchangePropertyKey.AGGREGATED_COMPLETED_BY, COMPLETED_BY_FORCE);
                        Exchange answer = onCompletion(key, exchange, exchange, false, false);
                        if (answer != null) {
                            onSubmitCompletion(key, answer);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        LOG.trace("Completed force completion of all groups task");

        if (total > 0) {
            LOG.debug("Forcing completion of all groups with {} exchanges", total);
        }
        return total;
    }

    public int forceDiscardingOfGroup(String key) {
        // must acquire the shared aggregation lock to be able to trigger force completion
        int total = 0;

        lock.lock();
        try {
            Exchange exchange = aggregationRepository.get(camelContext, key);
            if (exchange != null) {
                total = 1;
                LOG.trace("Force discarded triggered for correlation key: {}", key);
                // force discarding by setting aggregate failed as true
                onCompletion(key, exchange, exchange, false, true);
            }
        } finally {
            lock.unlock();
        }
        LOG.trace("Completed force discarded of group {}", key);

        if (total > 0) {
            LOG.debug("Forcing discarding of group {} with {} exchanges", key, total);
        }
        return total;
    }

    public int forceDiscardingOfAllGroups() {

        // only run if CamelContext has been fully started or is stopping
        boolean allow = camelContext.getStatus().isStarted() || camelContext.getStatus().isStopping();
        if (!allow) {
            LOG.warn("Cannot start force discarding of all groups because CamelContext({}) has not been started",
                    camelContext.getName());
            return 0;
        }

        LOG.trace("Starting force discarding of all groups task");

        // trigger completion for all in the repository
        Set<String> keys = aggregationRepository.getKeys();

        int total = 0;
        if (keys != null && !keys.isEmpty()) {
            // must acquire the shared aggregation lock to be able to trigger force completion
            lock.lock();
            total = keys.size();
            try {
                for (String key : keys) {
                    Exchange exchange = aggregationRepository.get(camelContext, key);
                    if (exchange != null) {
                        LOG.trace("Force discarded triggered for correlation key: {}", key);
                        // force discarding by setting aggregate failed as true
                        onCompletion(key, exchange, exchange, false, true);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        LOG.trace("Completed force discarding of all groups task");

        if (total > 0) {
            LOG.debug("Forcing discarding of all groups with {} exchanges", total);
        }
        return total;
    }

}
