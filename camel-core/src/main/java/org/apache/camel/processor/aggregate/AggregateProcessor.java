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
package org.apache.camel.processor.aggregate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.Traceable;
import org.apache.camel.spi.AggregationRepository;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.RecoverableAggregationRepository;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.DefaultTimeoutMap;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.LRUCache;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.TimeoutMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An implementation of the <a
 * href="http://camel.apache.org/aggregator2.html">Aggregator</a>
 * pattern where a batch of messages are processed (up to a maximum amount or
 * until some timeout is reached) and messages for the same correlation key are
 * combined together using some kind of {@link AggregationStrategy}
 * (by default the latest message is used) to compress many message exchanges
 * into a smaller number of exchanges.
 * <p/>
 * A good example of this is stock market data; you may be receiving 30,000
 * messages/second and you may want to throttle it right down so that multiple
 * messages for the same stock are combined (or just the latest message is used
 * and older prices are discarded). Another idea is to combine line item messages
 * together into a single invoice message.
 *
 * @version $Revision$
 */
public class AggregateProcessor extends ServiceSupport implements Processor, Navigate<Processor>, Traceable {

    private static final Log LOG = LogFactory.getLog(AggregateProcessor.class);

    private final Lock lock = new ReentrantLock();
    private final CamelContext camelContext;
    private final Processor processor;
    private final AggregationStrategy aggregationStrategy;
    private final Expression correlationExpression;
    private final ExecutorService executorService;
    private ScheduledExecutorService recoverService;
    // store correlation key -> exchange id in timeout map
    private TimeoutMap<Object, String> timeoutMap;
    private ExceptionHandler exceptionHandler = new LoggingExceptionHandler(getClass());
    private AggregationRepository<Object> aggregationRepository = new MemoryAggregationRepository();
    private Map<Object, Object> closedCorrelationKeys;
    private final Set<String> inProgressCompleteExchanges = new HashSet<String>();
    private final Map<String, RedeliveryData> redeliveryState = new ConcurrentHashMap<String, RedeliveryData>();
    // optional dead letter channel for exhausted recovered exchanges
    private Processor deadLetterProcessor;

    // keep booking about redelivery
    private class RedeliveryData {
        int redeliveryCounter;
        long redeliveryDelay;
    }

    // options
    private boolean ignoreBadCorrelationKeys;
    private Integer closeCorrelationKeyOnCompletion;
    private boolean parallelProcessing;

    // different ways to have completion triggered
    private boolean eagerCheckCompletion;
    private Predicate completionPredicate;
    private long completionTimeout;
    private Expression completionTimeoutExpression;
    private int completionSize;
    private Expression completionSizeExpression;
    private boolean completionFromBatchConsumer;
    private AtomicInteger batchConsumerCounter = new AtomicInteger();

    public AggregateProcessor(CamelContext camelContext, Processor processor,
                              Expression correlationExpression, AggregationStrategy aggregationStrategy,
                              ExecutorService executorService) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(processor, "processor");
        ObjectHelper.notNull(correlationExpression, "correlationExpression");
        ObjectHelper.notNull(aggregationStrategy, "aggregationStrategy");
        ObjectHelper.notNull(executorService, "executorService");
        this.camelContext = camelContext;
        this.processor = processor;
        this.correlationExpression = correlationExpression;
        this.aggregationStrategy = aggregationStrategy;
        this.executorService = executorService;
    }

    @Override
    public String toString() {
        return "AggregateProcessor[to: " + processor + "]";
    }

    public String getTraceLabel() {
        return "aggregate[" + correlationExpression + "]";
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(processor);
        return answer;
    }

    public boolean hasNext() {
        return processor != null;
    }

    public void process(Exchange exchange) throws Exception {
        // compute correlation expression
        Object key = correlationExpression.evaluate(exchange, Object.class);
        if (ObjectHelper.isEmpty(key)) {
            // we have a bad correlation key
            if (isIgnoreBadCorrelationKeys()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Correlation key could not be evaluated to a value. Exchange will be ignored: " + exchange);
                }
                return;
            } else {
                throw new CamelExchangeException("Correlation key could not be evaluated to a value", exchange);
            }
        }

        // is the correlation key closed?
        if (closedCorrelationKeys != null && closedCorrelationKeys.containsKey(key)) {
            throw new ClosedCorrelationKeyException(key, exchange);
        }

        // when memory based then its fast using synchronized, but if the aggregation repository is IO
        // bound such as JPA etc then concurrent aggregation per correlation key could
        // improve performance as we can run aggregation repository get/add in parallel
        try {
            lock.lock();
            doAggregation(key, exchange);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Aggregates the exchange with the given correlation key
     * <p/>
     * This method <b>must</b> be run synchronized as we cannot aggregate the same correlation key
     * in parallel.
     *
     * @param key      the correlation key
     * @param exchange the exchange
     * @return the aggregated exchange
     */
    private Exchange doAggregation(Object key, Exchange exchange) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("onAggregation +++ start +++ with correlation key: " + key);
        }

        Exchange answer;
        Exchange oldExchange = aggregationRepository.get(exchange.getContext(), key);
        Exchange newExchange = exchange;

        Integer size = 1;
        if (oldExchange != null) {
            size = oldExchange.getProperty(Exchange.AGGREGATED_SIZE, 0, Integer.class);
            size++;
        }

        // check if we are complete
        String complete = null;
        if (isEagerCheckCompletion()) {
            // put the current aggregated size on the exchange so its avail during completion check
            newExchange.setProperty(Exchange.AGGREGATED_SIZE, size);
            complete = isCompleted(key, newExchange);
            // remove it afterwards
            newExchange.removeProperty(Exchange.AGGREGATED_SIZE);
        }

        // prepare the exchanges for aggregation and aggregate it
        ExchangeHelper.prepareAggregation(oldExchange, newExchange);
        answer = onAggregation(oldExchange, exchange);
        // update the aggregated size
        answer.setProperty(Exchange.AGGREGATED_SIZE, size);

        // maybe we should check completion after the aggregation
        if (!isEagerCheckCompletion()) {
            complete = isCompleted(key, answer);
        }

        // only need to update aggregation repository if we are not complete
        if (complete == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("In progress aggregated exchange: " + answer + " with correlation key:" + key);
            }
            aggregationRepository.add(exchange.getContext(), key, answer);
        } else {
            // TODO: if we are completed from batch consumer then they should all complete (trigger that like timeout map)
            answer.setProperty(Exchange.AGGREGATED_COMPLETED_BY, complete);
            onCompletion(key, answer, false);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("onAggregation +++  end  +++ with correlation key: " + key);
        }

        return answer;
    }

    /**
     * Tests whether the given exchange is complete or not
     *
     * @param key       the correlation key
     * @param exchange  the incoming exchange
     * @return <tt>null</tt> if not completed, otherwise a String with the type that triggered the completion
     */
    protected String isCompleted(Object key, Exchange exchange) {
        if (getCompletionPredicate() != null) {
            boolean answer = getCompletionPredicate().matches(exchange);
            if (answer) {
                return "predicate";
            }
        }

        if (getCompletionSizeExpression() != null) {
            Integer value = getCompletionSizeExpression().evaluate(exchange, Integer.class);
            if (value != null && value > 0) {
                int size = exchange.getProperty(Exchange.AGGREGATED_SIZE, 1, Integer.class);
                if (size >= value) {
                    return "size";
                }
            }
        }
        if (getCompletionSize() > 0) {
            int size = exchange.getProperty(Exchange.AGGREGATED_SIZE, 1, Integer.class);
            if (size >= getCompletionSize()) {
                return "size";
            }
        }

        // timeout can be either evaluated based on an expression or from a fixed value
        // expression takes precedence
        boolean timeoutSet = false;
        if (getCompletionTimeoutExpression() != null) {
            Long value = getCompletionTimeoutExpression().evaluate(exchange, Long.class);
            if (value != null && value > 0) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Updating correlation key " + key + " to timeout after "
                            + value + " ms. as exchange received: " + exchange);
                }
                timeoutMap.put(key, exchange.getExchangeId(), value);
                timeoutSet = true;
            }
        }
        if (!timeoutSet && getCompletionTimeout() > 0) {
            // timeout is used so use the timeout map to keep an eye on this
            if (LOG.isTraceEnabled()) {
                LOG.trace("Updating correlation key " + key + " to timeout after "
                        + getCompletionTimeout() + " ms. as exchange received: " + exchange);
            }
            timeoutMap.put(key, exchange.getExchangeId(), getCompletionTimeout());
        }

        if (isCompletionFromBatchConsumer()) {
            batchConsumerCounter.incrementAndGet();
            int size = exchange.getProperty(Exchange.BATCH_SIZE, 0, Integer.class);
            if (size > 0 && batchConsumerCounter.intValue() >= size) {
                // batch consumer is complete then reset the counter
                batchConsumerCounter.set(0);
                return "consumer";
            }
        }

        // not complete
        return null;
    }

    protected Exchange onAggregation(Exchange oldExchange, Exchange newExchange) {
        return aggregationStrategy.aggregate(oldExchange, newExchange);
    }

    protected void onCompletion(final Object key, final Exchange exchange, boolean fromTimeout) {
        // store the correlation key as property
        exchange.setProperty(Exchange.AGGREGATED_CORRELATION_KEY, key);
        // remove from repository as its completed
        aggregationRepository.remove(exchange.getContext(), key, exchange);
        if (!fromTimeout && timeoutMap != null) {
            // cleanup timeout map if it was a incoming exchange which triggered the timeout (and not the timeout checker)
            timeoutMap.remove(key);
        }

        // this key has been closed so add it to the closed map
        if (closedCorrelationKeys != null) {
            closedCorrelationKeys.put(key, key);
        }

        onSubmitCompletion(key, exchange);
    }

    private void onSubmitCompletion(final Object key, final Exchange exchange) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Aggregation complete for correlation key " + key + " sending aggregated exchange: " + exchange);
        }

        // add this as in progress before we submit the task
        inProgressCompleteExchanges.add(exchange.getExchangeId());

        // send this exchange
        executorService.submit(new Runnable() {
            public void run() {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Processing aggregated exchange: " + exchange);
                }

                // add on completion task so we remember to update the inProgressCompleteExchanges
                exchange.addOnCompletion(new AggregateOnCompletion(exchange.getExchangeId()));

                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                } catch (Throwable t) {
                    // must catch throwable so we will handle all exceptions as the executor service will by default ignore them
                    exchange.setException(new CamelExchangeException("Error processing aggregated exchange", exchange, t));
                }

                // log exception if there was a problem
                if (exchange.getException() != null) {
                    // if there was an exception then let the exception handler handle it
                    getExceptionHandler().handleException("Error processing aggregated exchange", exchange, exchange.getException());
                } else {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Processing aggregated exchange: " + exchange + " complete.");
                    }
                }
            }
        });
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

    public boolean isIgnoreBadCorrelationKeys() {
        return ignoreBadCorrelationKeys;
    }

    public void setIgnoreBadCorrelationKeys(boolean ignoreBadCorrelationKeys) {
        this.ignoreBadCorrelationKeys = ignoreBadCorrelationKeys;
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

    public AggregationRepository<Object> getAggregationRepository() {
        return aggregationRepository;
    }

    public void setAggregationRepository(AggregationRepository<Object> aggregationRepository) {
        this.aggregationRepository = aggregationRepository;
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

        public void onFailure(Exchange exchange) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Aggregated exchange onFailure: " + exchange);
            }

            // must remember to remove in progress when we failed
            inProgressCompleteExchanges.remove(exchangeId);
            // do not remove redelivery state as we need it when we redeliver again later
        }

        public void onComplete(Exchange exchange) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Aggregated exchange onComplete: " + exchange);
            }

            // only confirm if we processed without a problem
            try {
                aggregationRepository.confirm(exchange.getContext(), exchangeId);
                // and remove redelivery state as well
                redeliveryState.remove(exchangeId);
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
    private final class AggregationTimeoutMap extends DefaultTimeoutMap<Object, String> {

        private AggregationTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
            super(executor, requestMapPollTimeMillis);
        }

        @Override
        public void onEviction(Object key, String exchangeId) {
            if (log.isDebugEnabled()) {
                log.debug("Completion timeout triggered for correlation key: " + key);
            }

            // get the aggregated exchange
            Exchange answer = aggregationRepository.get(camelContext, key);

            // indicate it was completed by timeout
            answer.setProperty(Exchange.AGGREGATED_COMPLETED_BY, "timeout");

            try {
                lock.lock();
                onCompletion(key, answer, true);
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * Background task that looks for aggregated exchanges to recover.
     */
    private final class RecoverTask implements Runnable {
        private final RecoverableAggregationRepository<Object> recoverable;

        private RecoverTask(RecoverableAggregationRepository<Object> recoverable) {
            this.recoverable = recoverable;
        }

        public void run() {
            // only run if CamelContext has been fully started
            if (!camelContext.getStatus().isStarted()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Recover check cannot start due CamelContext(" + camelContext.getName() + ") has not been started yet");
                }
                return;
            }

            LOG.trace("Starting recover check");

            Set<String> exchangeIds = recoverable.scan(camelContext);
            for (String exchangeId : exchangeIds) {

                // we may shutdown while doing recovery
                if (!isRunAllowed()) {
                    LOG.info("We are shutting down so stop recovering");
                    return;
                }

                boolean inProgress = inProgressCompleteExchanges.contains(exchangeId);
                if (inProgress) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Aggregated exchange with id: " + exchangeId + " is already in progress.");
                    }
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Loading aggregated exchange with id: " + exchangeId + " to be recovered.");
                    }
                    Exchange exchange = recoverable.recover(camelContext, exchangeId);
                    if (exchange != null) {
                        // get the correlation key
                        String key = exchange.getProperty(Exchange.AGGREGATED_CORRELATION_KEY, String.class);
                        // and mark it as redelivered
                        exchange.getIn().setHeader(Exchange.REDELIVERED, Boolean.TRUE);

                        // get the current redelivery data
                        RedeliveryData data = redeliveryState.get(exchange.getExchangeId());

                        // if we are exhausted, then move to dead letter channel
                        if (data != null && recoverable.getMaximumRedeliveries() > 0 && data.redeliveryCounter >= recoverable.getMaximumRedeliveries()) {
                            LOG.warn("The recovered exchange is exhausted after " + recoverable.getMaximumRedeliveries()
                                    + " attempts, will now be moved to dead letter channel: " + recoverable.getDeadLetterUri());

                            // send to DLC
                            try {
                                // set redelivery counter
                                exchange.getIn().setHeader(Exchange.REDELIVERY_COUNTER, data.redeliveryCounter);
                                deadLetterProcessor.process(exchange);
                            } catch (Exception e) {
                                exchange.setException(e);
                            }

                            // handle if failed
                            if (exchange.getException() != null) {
                                getExceptionHandler().handleException("Failed to move recovered Exchange to dead letter channel: " + recoverable.getDeadLetterUri(), exchange.getException());
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

                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Delivery attempt: " + data.redeliveryCounter + " to recover aggregated exchange with id: " + exchangeId + "");
                            }
                            // not exhaust so resubmit the recovered exchange
                            try {
                                lock.lock();
                                onSubmitCompletion(key, exchange);
                            } finally {
                                lock.unlock();
                            }
                        }
                    }
                }
            }

            LOG.trace("Recover check complete");
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (getCompletionTimeout() <= 0 && getCompletionSize() <= 0 && getCompletionPredicate() == null
                && !isCompletionFromBatchConsumer() && getCompletionTimeoutExpression() == null
                && getCompletionSizeExpression() == null) {
            throw new IllegalStateException("At least one of the completions options"
                    + " [completionTimeout, completionSize, completionPredicate, completionFromBatchConsumer] must be set");
        }

        if (getCloseCorrelationKeyOnCompletion() != null) {
            if (getCloseCorrelationKeyOnCompletion() > 0) {
                LOG.info("Using ClosedCorrelationKeys with a LRUCache with a capacity of " + getCloseCorrelationKeyOnCompletion());
                closedCorrelationKeys = new LRUCache<Object, Object>(getCloseCorrelationKeyOnCompletion());
            } else {
                LOG.info("Using ClosedCorrelationKeys with unbounded capacity");
                closedCorrelationKeys = new HashMap<Object, Object>();
            }

        }

        ServiceHelper.startServices(processor, aggregationRepository);

        // should we use recover checker
        if (aggregationRepository instanceof RecoverableAggregationRepository) {
            RecoverableAggregationRepository<Object> recoverable = (RecoverableAggregationRepository<Object>) aggregationRepository;
            if (recoverable.isUseRecovery()) {
                long interval = recoverable.getRecoveryIntervalInMillis();
                if (interval <= 0) {
                    throw new IllegalArgumentException("AggregationRepository has recovery enabled and the RecoveryInterval option must be a positive number, was: " + interval);
                }

                // create a background recover thread to check every interval
                recoverService = camelContext.getExecutorServiceStrategy().newScheduledThreadPool(this, "AggregateRecoverChecker", 1);
                Runnable recoverTask = new RecoverTask(recoverable);
                LOG.info("Using RecoverableAggregationRepository by scheduling recover checker to run every " + interval + " millis.");
                // use fixed delay so there is X interval between each run
                recoverService.scheduleWithFixedDelay(recoverTask, 1000L, interval, TimeUnit.MILLISECONDS);

                if (recoverable.getDeadLetterUri() != null) {
                    int max = recoverable.getMaximumRedeliveries();
                    if (max <= 0) {
                        throw new IllegalArgumentException("Option maximumRedeliveries must be a positive number, was: " + max);
                    }
                    LOG.info("After " + max + " failed redelivery attempts Exchanges will be moved to deadLetterUri: " + recoverable.getDeadLetterUri());
                    deadLetterProcessor = camelContext.getEndpoint(recoverable.getDeadLetterUri()).createProducer();
                }
            }
        }

        // start timeout service if its in use
        if (getCompletionTimeout() > 0 || getCompletionTimeoutExpression() != null) {
            ScheduledExecutorService scheduler = camelContext.getExecutorServiceStrategy().newScheduledThreadPool(this, "AggregateTimeoutChecker", 1);
            // check for timed out aggregated messages once every second
            timeoutMap = new AggregationTimeoutMap(scheduler, 1000L);
            ServiceHelper.startService(timeoutMap);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(timeoutMap, recoverService, aggregationRepository, processor);

        if (closedCorrelationKeys != null) {
            closedCorrelationKeys.clear();
        }
        redeliveryState.clear();
    }

    @Override
    protected void doShutdown() throws Exception {
        // cleanup when shutting down
        inProgressCompleteExchanges.clear();
    }

}