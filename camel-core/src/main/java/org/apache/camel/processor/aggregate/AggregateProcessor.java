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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.impl.LoggingExceptionHandler;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.Traceable;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.util.DefaultTimeoutMap;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.TimeoutMap;
import org.apache.camel.util.TimeoutMapEntry;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
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

    // TODO: Add support for parallelProcessing, setting custom ExecutorService like multicast

    private static final Log LOG = LogFactory.getLog(AggregateProcessor.class);

    private TimeoutMap<Object, Exchange> timeoutMap;
    private final Processor processor;
    private final AggregationStrategy aggregationStrategy;
    private final Expression correlationExpression;
    private ExecutorService executorService;
    private AggregationRepository<Object> aggregationRepository = new MemoryAggregationRepository();
    private Set<Object> closedCorrelationKeys = new HashSet<Object>();
    private ExceptionHandler exceptionHandler;

    // options
    private boolean ignoreBadCorrelationKeys;
    private boolean closeCorrelationKeyOnCompletion;
    private int concurrentConsumers = 1;

    // different ways to have completion triggered
    private boolean eagerCheckCompletion;
    private Predicate completionPredicate;
    private long completionTimeout;
    private int completionSize;
    private boolean completionFromBatchConsumer;
    private int batchConsumerCounter;

    public AggregateProcessor(Processor processor, Expression correlationExpression, AggregationStrategy aggregationStrategy) {
        ObjectHelper.notNull(processor, "processor");
        ObjectHelper.notNull(correlationExpression, "correlationExpression");
        ObjectHelper.notNull(aggregationStrategy, "aggregationStrategy");
        this.processor = processor;
        this.correlationExpression = correlationExpression;
        this.aggregationStrategy = aggregationStrategy;
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
        if (isCloseCorrelationKeyOnCompletion()) {
            if (closedCorrelationKeys.contains(key)) {
                throw new CamelExchangeException("Correlation key has been closed", exchange);
            }
        }

        doAggregation(key, exchange);
    }

    private synchronized Exchange doAggregation(Object key, Exchange exchange) {
        // TODO: lock this based on keys so we can run in parallel groups

        if (LOG.isTraceEnabled()) {
            LOG.trace("+++ start +++ onAggregation for key " + key);
        }

        Exchange answer;
        Exchange oldExchange = aggregationRepository.get(key);
        Exchange newExchange = exchange;

        Integer size = 1;
        if (oldExchange != null) {
            size = oldExchange.getProperty(Exchange.AGGREGATED_SIZE, 0, Integer.class);
            size++;
        }

        // check if we are complete
        boolean complete = false;
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
        answer.setProperty(Exchange.AGGREGATED_SIZE, size);

        // maybe we should check completion after the aggregation
        if (!isEagerCheckCompletion()) {
            // put the current aggregated size on the exchange so its avail during completion check
            answer.setProperty(Exchange.AGGREGATED_SIZE, size);
            complete = isCompleted(key, answer);
        }

        // only need to update aggregation repository if we are not complete
        if (!complete) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("In progress aggregated exchange: " + answer + " with correlation key:" + key);
            }
            aggregationRepository.add(key, answer);
        }

        if (complete) {
            onCompletion(key, answer, false);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("+++ end +++ onAggregation for key " + key + " with size " + size);
        }

        return answer;
    }

    protected boolean isCompleted(Object key, Exchange exchange) {
        if (getCompletionPredicate() != null) {
            boolean answer = getCompletionPredicate().matches(exchange);
            if (answer) {
                return true;
            }
        }

        if (getCompletionSize() > 0) {
            int size = exchange.getProperty(Exchange.AGGREGATED_SIZE, 1, Integer.class);
            if (size >= getCompletionSize()) {
                return true;
            }
        }

        if (getCompletionTimeout() > 0) {
            // timeout is used so use the timeout map to keep an eye on this
            if (LOG.isTraceEnabled()) {
                LOG.trace("Updating correlation key " + key + " to timeout after "
                        + getCompletionTimeout() + " ms. as exchange received: " + exchange);
            }
            timeoutMap.put(key, exchange, getCompletionTimeout());
        }

        if (isCompletionFromBatchConsumer()) {
            batchConsumerCounter++;
            int size = exchange.getProperty(Exchange.BATCH_SIZE, 0, Integer.class);
            if (size > 0 && batchConsumerCounter >= size) {
                // batch consumer is complete
                batchConsumerCounter = 0;
                return true;
            }
        }

        return false;
    }

    protected Exchange onAggregation(Exchange oldExchange, Exchange newExchange) {
        return aggregationStrategy.aggregate(oldExchange, newExchange);
    }

    protected void onCompletion(Object key, final Exchange exchange, boolean fromTimeout) {
        // remove from repository as its completed
        aggregationRepository.remove(key);
        if (!fromTimeout && timeoutMap != null) {
            // cleanup timeout map if it was a incoming exchange which triggered the timeout (and not the timeout checker)
            timeoutMap.remove(key);
        }

        // this key has been closed so add it to the closed map
        if (isCloseCorrelationKeyOnCompletion()) {
            closedCorrelationKeys.add(key);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Aggregation complete for correlation key " + key + " sending aggregated exchange: " + exchange);
        }

        // send this exchange
        executorService.submit(new Runnable() {
            public void run() {
                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    exchange.setException(e);
                } catch (Throwable t) {
                    // must catch throwable so we will handle all exceptions as the executor service will by default ignore them
                    exchange.setException(new CamelExchangeException("Error processing aggregated exchange", exchange, t));
                }

                // if there was an exception then let the exception handler handle it
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing aggregated exchange", exchange, exchange.getException());
                }
            }
        });
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
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

    public int getCompletionSize() {
        return completionSize;
    }

    public void setCompletionSize(int completionSize) {
        this.completionSize = completionSize;
    }

    public boolean isIgnoreBadCorrelationKeys() {
        return ignoreBadCorrelationKeys;
    }

    public void setIgnoreBadCorrelationKeys(boolean ignoreBadCorrelationKeys) {
        this.ignoreBadCorrelationKeys = ignoreBadCorrelationKeys;
    }

    public boolean isCloseCorrelationKeyOnCompletion() {
        return closeCorrelationKeyOnCompletion;
    }

    public void setCloseCorrelationKeyOnCompletion(boolean closeCorrelationKeyOnCompletion) {
        this.closeCorrelationKeyOnCompletion = closeCorrelationKeyOnCompletion;
    }

    public boolean isCompletionFromBatchConsumer() {
        return completionFromBatchConsumer;
    }

    public void setCompletionFromBatchConsumer(boolean completionFromBatchConsumer) {
        this.completionFromBatchConsumer = completionFromBatchConsumer;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(getClass());
        }
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Background tasks that looks for aggregated exchanges which is triggered by completion timeouts.
     */
    private class AggregationTimeoutMap extends DefaultTimeoutMap<Object, Exchange> {

        private AggregationTimeoutMap(ScheduledExecutorService executor, long requestMapPollTimeMillis) {
            super(executor, requestMapPollTimeMillis);
        }

        protected boolean isValidForEviction(TimeoutMapEntry<Object, Exchange> entry) {
            if (log.isDebugEnabled()) {
                log.debug("Completion timeout triggered for correlation key: " + entry.getKey());
            }
            onCompletion(entry.getKey(), entry.getValue(), true);
            return true;
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (getCompletionTimeout() <= 0 && getCompletionSize() <= 0 && getCompletionPredicate() == null) {
            throw new IllegalStateException("At least one of the completions options"
                    + " [completionTimeout, completionAggregatedSize, completionPredicate] must be set");
        }

        ServiceHelper.startService(aggregationRepository);

        if (executorService == null) {
            executorService = ExecutorServiceHelper.newFixedThreadPool(getConcurrentConsumers(), "AggregateProcessor", true);
        }

        // start timeout service if its in use
        if (getCompletionTimeout() > 0) {
            ScheduledExecutorService scheduler = ExecutorServiceHelper.newScheduledThreadPool(1, "AggregateProcessorTimeoutCompletion", true);
            timeoutMap = new AggregationTimeoutMap(scheduler, 1000L);
            ServiceHelper.startService(timeoutMap);
        }
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(timeoutMap);

        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }

        ServiceHelper.stopService(aggregationRepository);
        closedCorrelationKeys.clear();
    }

}
