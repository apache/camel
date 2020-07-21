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
package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Navigate;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExpressionComparator;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the <a href="http://camel.apache.org/resequencer.html">Resequencer</a>
 * which can reorder messages within a batch.
 */
public class Resequencer extends AsyncProcessorSupport implements Navigate<Processor>, IdAware, RouteIdAware, Traceable {

    public static final long DEFAULT_BATCH_TIMEOUT = 1000L;
    public static final int DEFAULT_BATCH_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(Resequencer.class);

    private String id;
    private String routeId;
    private long batchTimeout = DEFAULT_BATCH_TIMEOUT;
    private int batchSize = DEFAULT_BATCH_SIZE;
    private int outBatchSize;
    private boolean groupExchanges;
    private boolean batchConsumer;
    private boolean ignoreInvalidExchanges;
    private boolean reverse;
    private boolean allowDuplicates;
    private Predicate completionPredicate;
    private Expression expression;

    private final CamelContext camelContext;
    private final AsyncProcessor processor;
    private final Collection<Exchange> collection;
    private ExceptionHandler exceptionHandler;

    private final BatchSender sender;

    public Resequencer(CamelContext camelContext, Processor processor, Expression expression) {
        this(camelContext, processor, createSet(expression, false, false), expression);
    }

    public Resequencer(CamelContext camelContext, Processor processor, Expression expression,
                       boolean allowDuplicates, boolean reverse) {
        this(camelContext, processor, createSet(expression, allowDuplicates, reverse), expression);
    }

    public Resequencer(CamelContext camelContext, Processor processor, Set<Exchange> collection, Expression expression) {
        ObjectHelper.notNull(camelContext, "camelContext");
        ObjectHelper.notNull(processor, "processor");
        ObjectHelper.notNull(collection, "collection");
        ObjectHelper.notNull(expression, "expression");

        // wrap processor in UnitOfWork so what we send out of the batch runs in a UoW
        this.camelContext = camelContext;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
        this.collection = collection;
        this.expression = expression;
        this.sender = new BatchSender();
        this.exceptionHandler = new LoggingExceptionHandler(camelContext, getClass());
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "resequencer";
    }

    // Properties
    // -------------------------------------------------------------------------


    public Expression getExpression() {
        return expression;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public int getBatchSize() {
        return batchSize;
    }

    /**
     * Sets the <b>in</b> batch size. This is the number of incoming exchanges that this batch processor will
     * process before its completed. The default value is {@link #DEFAULT_BATCH_SIZE}.
     *
     * @param batchSize the size
     */
    public void setBatchSize(int batchSize) {
        // setting batch size to 0 or negative is like disabling it, so we set it as the max value
        // as the code logic is dependent on a batch size having 1..n value
        if (batchSize <= 0) {
            LOG.debug("Disabling batch size, will only be triggered by timeout");
            this.batchSize = Integer.MAX_VALUE;
        } else {
            this.batchSize = batchSize;
        }
    }

    public int getOutBatchSize() {
        return outBatchSize;
    }

    /**
     * Sets the <b>out</b> batch size. If the batch processor holds more exchanges than this out size then the
     * completion is triggered. Can for instance be used to ensure that this batch is completed when a certain
     * number of exchanges has been collected. By default this feature is <b>not</b> enabled.
     *
     * @param outBatchSize the size
     */
    public void setOutBatchSize(int outBatchSize) {
        this.outBatchSize = outBatchSize;
    }

    public long getBatchTimeout() {
        return batchTimeout;
    }

    public void setBatchTimeout(long batchTimeout) {
        this.batchTimeout = batchTimeout;
    }

    public boolean isGroupExchanges() {
        return groupExchanges;
    }

    public void setGroupExchanges(boolean groupExchanges) {
        this.groupExchanges = groupExchanges;
    }

    public boolean isBatchConsumer() {
        return batchConsumer;
    }

    public void setBatchConsumer(boolean batchConsumer) {
        this.batchConsumer = batchConsumer;
    }

    public boolean isIgnoreInvalidExchanges() {
        return ignoreInvalidExchanges;
    }

    public void setIgnoreInvalidExchanges(boolean ignoreInvalidExchanges) {
        this.ignoreInvalidExchanges = ignoreInvalidExchanges;
    }

    public boolean isReverse() {
        return reverse;
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public boolean isAllowDuplicates() {
        return allowDuplicates;
    }

    public void setAllowDuplicates(boolean allowDuplicates) {
        this.allowDuplicates = allowDuplicates;
    }

    public Predicate getCompletionPredicate() {
        return completionPredicate;
    }

    public void setCompletionPredicate(Predicate completionPredicate) {
        this.completionPredicate = completionPredicate;
    }

    public Processor getProcessor() {
        return processor;
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

    @Override
    public String getRouteId() {
        return routeId;
    }

    @Override
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected static Set<Exchange> createSet(Expression expression, boolean allowDuplicates, boolean reverse) {
        return createSet(new ExpressionComparator(expression), allowDuplicates, reverse);
    }

    protected static Set<Exchange> createSet(final Comparator<? super Exchange> comparator, boolean allowDuplicates, boolean reverse) {
        Comparator<? super Exchange> answer = comparator;

        if (reverse) {
            answer = comparator.reversed();
        }

        // if we allow duplicates then we need to cater for that in the comparator
        if (allowDuplicates) {
            // they are equal but we should allow duplicates so say that o2 is higher
            // so it will come next
            answer = answer.thenComparing((o1, o2) -> 1);
        }

        return new TreeSet<>(answer);
    }

    /**
     * A strategy method to decide if the "in" batch is completed. That is, whether the resulting exchanges in
     * the in queue should be drained to the "out" collection.
     */
    private boolean isInBatchCompleted(int num) {
        return num >= batchSize;
    }

    /**
     * A strategy method to decide if the "out" batch is completed. That is, whether the resulting exchange in
     * the out collection should be sent.
     */
    private boolean isOutBatchCompleted() {
        if (outBatchSize == 0) {
            // out batch is disabled, so go ahead and send.
            return true;
        }
        return collection.size() > 0 && collection.size() >= outBatchSize;
    }

    /**
     * Strategy Method to process an exchange in the batch. This method allows derived classes to perform
     * custom processing before or after an individual exchange is processed
     */
    protected void processExchange(Exchange exchange) {
        processor.process(exchange, sync -> postProcess(exchange));
    }

    protected void postProcess(Exchange exchange) {
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing aggregated exchange: " + exchange, exchange.getException());
        }
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processor);
        sender.start();
    }

    @Override
    protected void doStop() throws Exception {
        sender.cancel();
        ServiceHelper.stopService(processor);
        collection.clear();
    }

    /**
     * Enqueues an exchange for later batch processing.
     */
    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            // if batch consumer is enabled then we need to adjust the batch size
            // with the size from the batch consumer
            if (isBatchConsumer()) {
                int size = exchange.getProperty(Exchange.BATCH_SIZE, Integer.class);
                if (batchSize != size) {
                    batchSize = size;
                    LOG.trace("Using batch consumer completion, so setting batch size to: {}", batchSize);
                }
            }

            // validate that the exchange can be used
            if (!isValid(exchange)) {
                if (isIgnoreInvalidExchanges()) {
                    LOG.debug("Invalid Exchange. This Exchange will be ignored: {}", exchange);
                } else {
                    throw new CamelExchangeException("Exchange is not valid to be used by the BatchProcessor", exchange);
                }
            } else {
                // exchange is valid so enqueue the exchange
                sender.enqueueExchange(exchange);
            }
        } catch (Throwable e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    /**
     * Is the given exchange valid to be used.
     *
     * @param exchange the given exchange
     * @return <tt>true</tt> if valid, <tt>false</tt> otherwise
     */
    private boolean isValid(Exchange exchange) {
        Object result = null;
        try {
            result = expression.evaluate(exchange, Object.class);
        } catch (Exception e) {
            LOG.debug("Error evaluating expression: " + expression + ". This exception is ignored.", e);
        }
        return result != null;
    }

    /**
     * Sender thread for queued-up exchanges.
     */
    private class BatchSender extends Thread {

        private Queue<Exchange> queue;
        private Lock queueLock = new ReentrantLock();
        private final AtomicBoolean exchangeEnqueued = new AtomicBoolean();
        private final Queue<String> completionPredicateMatched = new ConcurrentLinkedQueue<>();
        private Condition exchangeEnqueuedCondition = queueLock.newCondition();

        BatchSender() {
            super(camelContext.getExecutorServiceManager().resolveThreadName("Batch Sender"));
            this.queue = new LinkedList<>();
        }

        @Override
        public void run() {
            // Wait until one of either:
            // * an exchange being queued;
            // * the batch timeout expiring; or
            // * the thread being cancelled.
            //
            // If an exchange is queued then we need to determine whether the
            // batch is complete. If it is complete then we send out the batched
            // exchanges. Otherwise we move back into our wait state.
            //
            // If the batch times out then we send out the batched exchanges
            // collected so far.
            //
            // If we receive an interrupt then all blocking operations are
            // interrupted and our thread terminates.
            //
            // The goal of the following algorithm in terms of synchronisation
            // is to provide fine grained locking i.e. retaining the lock only
            // when required. Special consideration is given to releasing the
            // lock when calling an overloaded method i.e. sendExchanges.
            // Unlocking is important as the process of sending out the exchanges
            // would otherwise block new exchanges from being queued.

            queueLock.lock();
            try {
                do {
                    try {
                        if (!exchangeEnqueued.get()) {
                            LOG.trace("Waiting for new exchange to arrive or batchTimeout to occur after {} ms.", batchTimeout);
                            exchangeEnqueuedCondition.await(batchTimeout, TimeUnit.MILLISECONDS);
                        }

                        // if the completion predicate was triggered then there is an exchange id which denotes when to complete
                        String id = null;
                        if (!completionPredicateMatched.isEmpty()) {
                            id = completionPredicateMatched.poll();
                        }

                        if (id != null || !exchangeEnqueued.get()) {
                            if (id != null) {
                                LOG.trace("Collecting exchanges to be aggregated triggered by completion predicate");
                            } else {
                                LOG.trace("Collecting exchanges to be aggregated triggered by batch timeout");
                            }
                            drainQueueTo(collection, batchSize, id);
                        } else {
                            exchangeEnqueued.set(false);
                            boolean drained = false;
                            while (isInBatchCompleted(queue.size())) {
                                drained = true;
                                drainQueueTo(collection, batchSize, id);
                            }
                            if (drained) {
                                LOG.trace("Collecting exchanges to be aggregated triggered by new exchanges received");
                            }

                            if (!isOutBatchCompleted()) {
                                continue;
                            }
                        }

                        queueLock.unlock();
                        try {
                            try {
                                sendExchanges();
                            } catch (Throwable t) {
                                // a fail safe to handle all exceptions being thrown
                                getExceptionHandler().handleException(t);
                            }
                        } finally {
                            queueLock.lock();
                        }

                    } catch (InterruptedException e) {
                        break;
                    }

                } while (isRunAllowed());

            } finally {
                queueLock.unlock();
            }
        }

        /**
         * This method should be called with queueLock held
         */
        private void drainQueueTo(Collection<Exchange> collection, int batchSize, String exchangeId) {
            for (int i = 0; i < batchSize; ++i) {
                Exchange e = queue.poll();
                if (e != null) {
                    try {
                        collection.add(e);
                    } catch (Exception t) {
                        e.setException(t);
                    } catch (Throwable t) {
                        getExceptionHandler().handleException(t);
                    }
                    if (exchangeId != null && exchangeId.equals(e.getExchangeId())) {
                        // this batch is complete so stop draining
                        break;
                    }
                } else {
                    break;
                }
            }
        }

        public void cancel() {
            interrupt();
        }

        public void enqueueExchange(Exchange exchange) {
            LOG.debug("Received exchange to be batched: {}", exchange);
            queueLock.lock();
            try {
                // pre test whether the completion predicate matched
                if (completionPredicate != null) {
                    boolean matches = completionPredicate.matches(exchange);
                    if (matches) {
                        LOG.trace("Exchange matched completion predicate: {}", exchange);
                        // add this exchange to the list of exchanges which marks the batch as complete
                        completionPredicateMatched.add(exchange.getExchangeId());
                    }
                }
                queue.add(exchange);
                exchangeEnqueued.set(true);
                exchangeEnqueuedCondition.signal();
            } finally {
                queueLock.unlock();
            }
        }

        private void sendExchanges() throws Exception {
            Iterator<Exchange> iter = collection.iterator();
            while (iter.hasNext()) {
                Exchange exchange = iter.next();
                iter.remove();
                LOG.debug("Sending aggregated exchange: {}", exchange);
                processExchange(exchange);
            }
        }
    }

}
