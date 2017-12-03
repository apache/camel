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
package org.apache.camel.processor;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.StreamCache;
import org.apache.camel.Traceable;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.processor.aggregate.CompletionAwareAggregationStrategy;
import org.apache.camel.processor.aggregate.DelegateAggregationStrategy;
import org.apache.camel.processor.aggregate.TimeoutAwareAggregationStrategy;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TracedRouteNodes;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorConverterHelper;
import org.apache.camel.util.AsyncProcessorHelper;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.EventHelper;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.concurrent.AtomicException;
import org.apache.camel.util.concurrent.AtomicExchange;
import org.apache.camel.util.concurrent.SubmitOrderedCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.notNull;


/**
 * Implements the Multicast pattern to send a message exchange to a number of
 * endpoints, each endpoint receiving a copy of the message exchange.
 *
 * @version
 * @see Pipeline
 */
public class MulticastProcessor extends ServiceSupport implements AsyncProcessor, Navigate<Processor>, Traceable, IdAware {

    private static final Logger LOG = LoggerFactory.getLogger(MulticastProcessor.class);

    /**
     * Class that represent each step in the multicast route to do
     */
    static final class DefaultProcessorExchangePair implements ProcessorExchangePair {
        private final int index;
        private final Processor processor;
        private final Processor prepared;
        private final Exchange exchange;

        private DefaultProcessorExchangePair(int index, Processor processor, Processor prepared, Exchange exchange) {
            this.index = index;
            this.processor = processor;
            this.prepared = prepared;
            this.exchange = exchange;
        }

        public int getIndex() {
            return index;
        }

        public Exchange getExchange() {
            return exchange;
        }

        public Producer getProducer() {
            if (processor instanceof Producer) {
                return (Producer) processor;
            }
            return null;
        }

        public Processor getProcessor() {
            return prepared;
        }

        public void begin() {
            // noop
        }

        public void done() {
            // noop
        }

    }

    /**
     * Class that represents prepared fine grained error handlers when processing multicasted/splitted exchanges
     * <p/>
     * See the <tt>createProcessorExchangePair</tt> and <tt>createErrorHandler</tt> methods.
     */
    static final class PreparedErrorHandler extends KeyValueHolder<RouteContext, Processor> {

        PreparedErrorHandler(RouteContext key, Processor value) {
            super(key, value);
        }

    }

    protected final Processor onPrepare;
    private final CamelContext camelContext;
    private String id;
    private Collection<Processor> processors;
    private final AggregationStrategy aggregationStrategy;
    private final boolean parallelProcessing;
    private final boolean streaming;
    private final boolean parallelAggregate;
    private final boolean stopOnAggregateException;
    private final boolean stopOnException;
    private final ExecutorService executorService;
    private final boolean shutdownExecutorService;
    private ExecutorService aggregateExecutorService;
    private final long timeout;
    private final ConcurrentMap<PreparedErrorHandler, Processor> errorHandlers = new ConcurrentHashMap<PreparedErrorHandler, Processor>();
    private final boolean shareUnitOfWork;

    public MulticastProcessor(CamelContext camelContext, Collection<Processor> processors) {
        this(camelContext, processors, null);
    }

    public MulticastProcessor(CamelContext camelContext, Collection<Processor> processors, AggregationStrategy aggregationStrategy) {
        this(camelContext, processors, aggregationStrategy, false, null, false, false, false, 0, null, false, false);
    }

    @Deprecated
    public MulticastProcessor(CamelContext camelContext, Collection<Processor> processors, AggregationStrategy aggregationStrategy,
                              boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService,
                              boolean streaming, boolean stopOnException, long timeout, Processor onPrepare, boolean shareUnitOfWork) {
        this(camelContext, processors, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService,
                streaming, stopOnException, timeout, onPrepare, shareUnitOfWork, false);
    }

    public MulticastProcessor(CamelContext camelContext, Collection<Processor> processors, AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                              ExecutorService executorService, boolean shutdownExecutorService, boolean streaming, boolean stopOnException, long timeout, Processor onPrepare,
                              boolean shareUnitOfWork, boolean parallelAggregate) {
        this(camelContext, processors, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService, streaming, stopOnException, timeout, onPrepare,
             shareUnitOfWork, false, false);
    }
    
    public MulticastProcessor(CamelContext camelContext, Collection<Processor> processors, AggregationStrategy aggregationStrategy,
                              boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService, boolean streaming,
                              boolean stopOnException, long timeout, Processor onPrepare, boolean shareUnitOfWork,
                              boolean parallelAggregate, boolean stopOnAggregateException) {
        notNull(camelContext, "camelContext");
        this.camelContext = camelContext;
        this.processors = processors;
        this.aggregationStrategy = aggregationStrategy;
        this.executorService = executorService;
        this.shutdownExecutorService = shutdownExecutorService;
        this.streaming = streaming;
        this.stopOnException = stopOnException;
        // must enable parallel if executor service is provided
        this.parallelProcessing = parallelProcessing || executorService != null;
        this.timeout = timeout;
        this.onPrepare = onPrepare;
        this.shareUnitOfWork = shareUnitOfWork;
        this.parallelAggregate = parallelAggregate;
        this.stopOnAggregateException = stopOnAggregateException;
    }

    @Override
    public String toString() {
        return "Multicast[" + getProcessors() + "]";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTraceLabel() {
        return "multicast";
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        final AtomicExchange result = new AtomicExchange();
        Iterable<ProcessorExchangePair> pairs = null;

        try {
            boolean sync = true;

            pairs = createProcessorExchangePairs(exchange);

            if (isParallelProcessing()) {
                // ensure an executor is set when running in parallel
                ObjectHelper.notNull(executorService, "executorService", this);
                doProcessParallel(exchange, result, pairs, isStreaming(), callback);
            } else {
                sync = doProcessSequential(exchange, result, pairs, callback);
            }

            if (!sync) {
                // the remainder of the multicast will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }
        } catch (Throwable e) {
            exchange.setException(e);
            // unexpected exception was thrown, maybe from iterator etc. so do not regard as exhausted
            // and do the done work
            doDone(exchange, null, pairs, callback, true, false);
            return true;
        }

        // multicasting was processed successfully
        // and do the done work
        Exchange subExchange = result.get() != null ? result.get() : null;
        doDone(exchange, subExchange, pairs, callback, true, true);
        return true;
    }

    protected void doProcessParallel(final Exchange original, final AtomicExchange result, final Iterable<ProcessorExchangePair> pairs,
                                     final boolean streaming, final AsyncCallback callback) throws Exception {

        ObjectHelper.notNull(executorService, "ExecutorService", this);
        ObjectHelper.notNull(aggregateExecutorService, "AggregateExecutorService", this);

        final CompletionService<Exchange> completion;
        if (streaming) {
            // execute tasks in parallel+streaming and aggregate in the order they are finished (out of order sequence)
            completion = new ExecutorCompletionService<Exchange>(executorService);
        } else {
            // execute tasks in parallel and aggregate in the order the tasks are submitted (in order sequence)
            completion = new SubmitOrderedCompletionService<Exchange>(executorService);
        }

        final AtomicInteger total = new AtomicInteger(0);
        final Iterator<ProcessorExchangePair> it = pairs.iterator();

        if (it.hasNext()) {
            // when parallel then aggregate on the fly
            final AtomicBoolean running = new AtomicBoolean(true);
            final AtomicBoolean allTasksSubmitted = new AtomicBoolean();
            final CountDownLatch aggregationOnTheFlyDone = new CountDownLatch(1);
            final AtomicException executionException = new AtomicException();

            // issue task to execute in separate thread so it can aggregate on-the-fly
            // while we submit new tasks, and those tasks complete concurrently
            // this allows us to optimize work and reduce memory consumption
            final AggregateOnTheFlyTask aggregateOnTheFlyTask = new AggregateOnTheFlyTask(result, original, total, completion, running,
                    aggregationOnTheFlyDone, allTasksSubmitted, executionException);
            final AtomicBoolean aggregationTaskSubmitted = new AtomicBoolean();

            LOG.trace("Starting to submit parallel tasks");
            
            try {
                while (it.hasNext()) {
                    final ProcessorExchangePair pair = it.next();
                    // in case the iterator returns null then continue to next
                    if (pair == null) {
                        continue;
                    }
    
                    final Exchange subExchange = pair.getExchange();
                    updateNewExchange(subExchange, total.intValue(), pairs, it);
    
                    completion.submit(new Callable<Exchange>() {
                        public Exchange call() throws Exception {
                            // start the aggregation task at this stage only in order not to pile up too many threads
                            if (aggregationTaskSubmitted.compareAndSet(false, true)) {
                                // but only submit the aggregation task once
                                aggregateExecutorService.submit(aggregateOnTheFlyTask);
                            }
    
                            if (!running.get()) {
                                // do not start processing the task if we are not running
                                return subExchange;
                            }
    
                            try {
                                doProcessParallel(pair);
                            } catch (Throwable e) {
                                subExchange.setException(e);
                            }
    
                            // Decide whether to continue with the multicast or not; similar logic to the Pipeline
                            Integer number = getExchangeIndex(subExchange);
                            boolean continueProcessing = PipelineHelper.continueProcessing(subExchange, "Parallel processing failed for number " + number, LOG);
                            if (stopOnException && !continueProcessing) {
                                // signal to stop running
                                running.set(false);
                                // throw caused exception
                                if (subExchange.getException() != null) {
                                    // wrap in exception to explain where it failed
                                    CamelExchangeException cause = new CamelExchangeException("Parallel processing failed for number " + number, subExchange, subExchange.getException());
                                    subExchange.setException(cause);
                                }
                            }
    
                            LOG.trace("Parallel processing complete for exchange: {}", subExchange);
                            return subExchange;
                        }
                    });
    
                    total.incrementAndGet();
                }
            } catch (Throwable e) {
                // The methods it.hasNext and it.next can throw RuntimeExceptions when custom iterators are implemented.
                // We have to catch the exception here otherwise the aggregator threads would pile up.
                if (e instanceof Exception) {
                    executionException.set((Exception) e);
                } else {
                    executionException.set(ObjectHelper.wrapRuntimeCamelException(e));
                }
            }

            // signal all tasks has been submitted
            LOG.trace("Signaling that all {} tasks has been submitted.", total.get());
            allTasksSubmitted.set(true);

            // its to hard to do parallel async routing so we let the caller thread be synchronously
            // and have it pickup the replies and do the aggregation (eg we use a latch to wait)
            // wait for aggregation to be done
            LOG.debug("Waiting for on-the-fly aggregation to complete aggregating {} responses for exchangeId: {}", total.get(), original.getExchangeId());
            aggregationOnTheFlyDone.await();

            // did we fail for whatever reason, if so throw that caused exception
            if (executionException.get() != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Parallel processing failed due {}", executionException.get().getMessage());
                }
                throw executionException.get();
            }
        }

        // no everything is okay so we are done
        LOG.debug("Done parallel processing {} exchanges", total);
    }

    /**
     * Boss worker to control aggregate on-the-fly for completed tasks when using parallel processing.
     * <p/>
     * This ensures lower memory consumption as we do not need to keep all completed tasks in memory
     * before we perform aggregation. Instead this separate thread will run and aggregate when new
     * completed tasks is done.
     * <p/>
     * The logic is fairly complex as this implementation has to keep track how far it got, and also
     * signal back to the <i>main</t> thread when its done, so the <i>main</t> thread can continue
     * processing when the entire splitting is done.
     */
    private final class AggregateOnTheFlyTask implements Runnable {

        private final AtomicExchange result;
        private final Exchange original;
        private final AtomicInteger total;
        private final CompletionService<Exchange> completion;
        private final AtomicBoolean running;
        private final CountDownLatch aggregationOnTheFlyDone;
        private final AtomicBoolean allTasksSubmitted;
        private final AtomicException executionException;

        private AggregateOnTheFlyTask(AtomicExchange result, Exchange original, AtomicInteger total,
                                      CompletionService<Exchange> completion, AtomicBoolean running,
                                      CountDownLatch aggregationOnTheFlyDone, AtomicBoolean allTasksSubmitted,
                                      AtomicException executionException) {
            this.result = result;
            this.original = original;
            this.total = total;
            this.completion = completion;
            this.running = running;
            this.aggregationOnTheFlyDone = aggregationOnTheFlyDone;
            this.allTasksSubmitted = allTasksSubmitted;
            this.executionException = executionException;
        }

        public void run() {
            LOG.trace("Aggregate on the fly task started for exchangeId: {}", original.getExchangeId());

            try {
                aggregateOnTheFly();
            } catch (Throwable e) {
                if (e instanceof Exception) {
                    executionException.set((Exception) e);
                } else {
                    executionException.set(ObjectHelper.wrapRuntimeCamelException(e));
                }
            } finally {
                // must signal we are done so the latch can open and let the other thread continue processing
                LOG.debug("Signaling we are done aggregating on the fly for exchangeId: {}", original.getExchangeId());
                LOG.trace("Aggregate on the fly task done for exchangeId: {}", original.getExchangeId());
                aggregationOnTheFlyDone.countDown();
            }
        }

        private void aggregateOnTheFly() throws InterruptedException, ExecutionException {
            final AtomicBoolean timedOut = new AtomicBoolean();
            boolean stoppedOnException = false;
            final StopWatch watch = new StopWatch();
            final AtomicInteger aggregated = new AtomicInteger();
            boolean done = false;
            // not a for loop as on the fly may still run
            while (!done) {
                // check if we have already aggregate everything
                if (allTasksSubmitted.get() && aggregated.intValue() >= total.get()) {
                    LOG.debug("Done aggregating {} exchanges on the fly.", aggregated);
                    break;
                }

                Future<Exchange> future;
                if (timedOut.get()) {
                    // we are timed out but try to grab if some tasks has been completed
                    // poll will return null if no tasks is present
                    future = completion.poll();
                    LOG.trace("Polled completion task #{} after timeout to grab already completed tasks: {}", aggregated, future);
                } else if (timeout > 0) {
                    long left = timeout - watch.taken();
                    if (left < 0) {
                        left = 0;
                    }
                    LOG.trace("Polling completion task #{} using timeout {} millis.", aggregated, left);
                    future = completion.poll(left, TimeUnit.MILLISECONDS);
                } else {
                    LOG.trace("Polling completion task #{}", aggregated);
                    // we must not block so poll every second
                    future = completion.poll(1, TimeUnit.SECONDS);
                    if (future == null) {
                        // and continue loop which will recheck if we are done
                        continue;
                    }
                }

                if (future == null) {
                    ParallelAggregateTimeoutTask task = new ParallelAggregateTimeoutTask(original, result, completion, aggregated, total, timedOut);
                    if (parallelAggregate) {
                        aggregateExecutorService.submit(task);
                    } else {
                        // in non parallel mode then just run the task
                        task.run();
                    }
                } else {
                    // there is a result to aggregate
                    Exchange subExchange = future.get();

                    // Decide whether to continue with the multicast or not; similar logic to the Pipeline
                    Integer number = getExchangeIndex(subExchange);
                    boolean continueProcessing = PipelineHelper.continueProcessing(subExchange, "Parallel processing failed for number " + number, LOG);
                    if (stopOnException && !continueProcessing) {
                        // we want to stop on exception and an exception or failure occurred
                        // this is similar to what the pipeline does, so we should do the same to not surprise end users
                        // so we should set the failed exchange as the result and break out
                        result.set(subExchange);
                        stoppedOnException = true;
                        break;
                    }

                    // we got a result so aggregate it
                    ParallelAggregateTask task = new ParallelAggregateTask(result, subExchange, aggregated);
                    if (parallelAggregate) {
                        aggregateExecutorService.submit(task);
                    } else {
                        // in non parallel mode then just run the task
                        task.run();
                    }
                }
            }

            if (timedOut.get() || stoppedOnException) {
                if (timedOut.get()) {
                    LOG.debug("Cancelling tasks due timeout after {} millis.", timeout);
                }
                if (stoppedOnException) {
                    LOG.debug("Cancelling tasks due stopOnException.");
                }
                // cancel tasks as we timed out (its safe to cancel done tasks)
                running.set(false);
            }
        }
    }

    /**
     * Worker task to aggregate the old and new exchange on-the-fly for completed tasks when using parallel processing.
     */
    private final class ParallelAggregateTask implements Runnable {

        private final AtomicExchange result;
        private final Exchange subExchange;
        private final AtomicInteger aggregated;

        private ParallelAggregateTask(AtomicExchange result, Exchange subExchange, AtomicInteger aggregated) {
            this.result = result;
            this.subExchange = subExchange;
            this.aggregated = aggregated;
        }

        @Override
        public void run() {
            try {
                if (parallelAggregate) {
                    doAggregateInternal(getAggregationStrategy(subExchange), result, subExchange);
                } else {
                    doAggregate(getAggregationStrategy(subExchange), result, subExchange);
                }
            } catch (Throwable e) {
                if (isStopOnAggregateException()) {
                    throw e;
                } else {
                    // wrap in exception to explain where it failed
                    CamelExchangeException cex = new CamelExchangeException("Parallel processing failed for number " + aggregated.get(), subExchange, e);
                    subExchange.setException(cex);
                    LOG.debug(cex.getMessage(), cex);
                }
            } finally {
                aggregated.incrementAndGet();
            }
        }
    }

    /**
     * Worker task to aggregate the old and new exchange on-the-fly for completed tasks when using parallel processing.
     */
    private final class ParallelAggregateTimeoutTask implements Runnable {

        private final Exchange original;
        private final AtomicExchange result;
        private final CompletionService<Exchange> completion;
        private final AtomicInteger aggregated;
        private final AtomicInteger total;
        private final AtomicBoolean timedOut;

        private ParallelAggregateTimeoutTask(Exchange original, AtomicExchange result, CompletionService<Exchange> completion,
                                             AtomicInteger aggregated, AtomicInteger total, AtomicBoolean timedOut) {
            this.original = original;
            this.result = result;
            this.completion = completion;
            this.aggregated = aggregated;
            this.total = total;
            this.timedOut = timedOut;
        }

        @Override
        public void run() {
            AggregationStrategy strategy = getAggregationStrategy(null);
            if (strategy instanceof DelegateAggregationStrategy) {
                strategy = ((DelegateAggregationStrategy) strategy).getDelegate();
            }
            if (strategy instanceof TimeoutAwareAggregationStrategy) {
                // notify the strategy we timed out
                Exchange oldExchange = result.get();
                if (oldExchange == null) {
                    // if they all timed out the result may not have been set yet, so use the original exchange
                    oldExchange = original;
                }
                ((TimeoutAwareAggregationStrategy) strategy).timeout(oldExchange, aggregated.intValue(), total.intValue(), timeout);
            } else {
                // log a WARN we timed out since it will not be aggregated and the Exchange will be lost
                LOG.warn("Parallel processing timed out after {} millis for number {}. This task will be cancelled and will not be aggregated.", timeout, aggregated.intValue());
            }
            LOG.debug("Timeout occurred after {} millis for number {} task.", timeout, aggregated.intValue());
            timedOut.set(true);

            // mark that index as timed out, which allows us to try to retrieve
            // any already completed tasks in the next loop
            if (completion instanceof SubmitOrderedCompletionService) {
                ((SubmitOrderedCompletionService<?>) completion).timeoutTask();
            }

            // we timed out so increment the counter
            aggregated.incrementAndGet();
        }
    }

    protected boolean doProcessSequential(Exchange original, AtomicExchange result, Iterable<ProcessorExchangePair> pairs, AsyncCallback callback) throws Exception {
        AtomicInteger total = new AtomicInteger();
        Iterator<ProcessorExchangePair> it = pairs.iterator();

        while (it.hasNext()) {
            ProcessorExchangePair pair = it.next();
            // in case the iterator returns null then continue to next
            if (pair == null) {
                continue;
            }
            Exchange subExchange = pair.getExchange();
            updateNewExchange(subExchange, total.get(), pairs, it);

            boolean sync = doProcessSequential(original, result, pairs, it, pair, callback, total);
            if (!sync) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", pair.getExchange().getExchangeId());
                }
                // the remainder of the multicast will be completed async
                // so we break out now, then the callback will be invoked which then continue routing from where we left here
                return false;
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Processing exchangeId: {} is continued being processed synchronously", pair.getExchange().getExchangeId());
            }

            // Decide whether to continue with the multicast or not; similar logic to the Pipeline
            // remember to test for stop on exception and aggregate before copying back results
            boolean continueProcessing = PipelineHelper.continueProcessing(subExchange, "Sequential processing failed for number " + total.get(), LOG);
            if (stopOnException && !continueProcessing) {
                if (subExchange.getException() != null) {
                    // wrap in exception to explain where it failed
                    CamelExchangeException cause = new CamelExchangeException("Sequential processing failed for number " + total.get(), subExchange, subExchange.getException());
                    subExchange.setException(cause);
                }
                // we want to stop on exception, and the exception was handled by the error handler
                // this is similar to what the pipeline does, so we should do the same to not surprise end users
                // so we should set the failed exchange as the result and be done
                result.set(subExchange);
                return true;
            }

            LOG.trace("Sequential processing complete for number {} exchange: {}", total, subExchange);

            if (parallelAggregate) {
                doAggregateInternal(getAggregationStrategy(subExchange), result, subExchange);
            } else {
                doAggregate(getAggregationStrategy(subExchange), result, subExchange);
            }

            total.incrementAndGet();
        }

        LOG.debug("Done sequential processing {} exchanges", total);

        return true;
    }

    private boolean doProcessSequential(final Exchange original, final AtomicExchange result,
                                        final Iterable<ProcessorExchangePair> pairs, final Iterator<ProcessorExchangePair> it,
                                        final ProcessorExchangePair pair, final AsyncCallback callback, final AtomicInteger total) {
        boolean sync = true;

        final Exchange exchange = pair.getExchange();
        Processor processor = pair.getProcessor();
        final Producer producer = pair.getProducer();

        TracedRouteNodes traced = exchange.getUnitOfWork() != null ? exchange.getUnitOfWork().getTracedRouteNodes() : null;

        try {
            // prepare tracing starting from a new block
            if (traced != null) {
                traced.pushBlock();
            }

            StopWatch sw = null;
            if (producer != null) {
                boolean sending = EventHelper.notifyExchangeSending(exchange.getContext(), exchange, producer.getEndpoint());
                if (sending) {
                    sw = new StopWatch();
                }
            }

            // compute time taken if sending to another endpoint
            final StopWatch watch = sw;

            // let the prepared process it, remember to begin the exchange pair
            AsyncProcessor async = AsyncProcessorConverterHelper.convert(processor);
            pair.begin();
            sync = async.process(exchange, new AsyncCallback() {
                public void done(boolean doneSync) {
                    // we are done with the exchange pair
                    pair.done();

                    // okay we are done, so notify the exchange was sent
                    if (producer != null && watch != null) {
                        long timeTaken = watch.taken();
                        Endpoint endpoint = producer.getEndpoint();
                        // emit event that the exchange was sent to the endpoint
                        EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
                    }

                    // we only have to handle async completion of the routing slip
                    if (doneSync) {
                        return;
                    }

                    // continue processing the multicast asynchronously
                    Exchange subExchange = exchange;

                    // Decide whether to continue with the multicast or not; similar logic to the Pipeline
                    // remember to test for stop on exception and aggregate before copying back results
                    boolean continueProcessing = PipelineHelper.continueProcessing(subExchange, "Sequential processing failed for number " + total.get(), LOG);
                    if (stopOnException && !continueProcessing) {
                        if (subExchange.getException() != null) {
                            // wrap in exception to explain where it failed
                            subExchange.setException(new CamelExchangeException("Sequential processing failed for number " + total, subExchange, subExchange.getException()));
                        } else {
                            // we want to stop on exception, and the exception was handled by the error handler
                            // this is similar to what the pipeline does, so we should do the same to not surprise end users
                            // so we should set the failed exchange as the result and be done
                            result.set(subExchange);
                        }
                        // and do the done work
                        doDone(original, subExchange, pairs, callback, false, true);
                        return;
                    }

                    try {
                        if (parallelAggregate) {
                            doAggregateInternal(getAggregationStrategy(subExchange), result, subExchange);
                        } else {
                            doAggregate(getAggregationStrategy(subExchange), result, subExchange);
                        }
                    } catch (Throwable e) {
                        // wrap in exception to explain where it failed
                        subExchange.setException(new CamelExchangeException("Sequential processing failed for number " + total, subExchange, e));
                        // and do the done work
                        doDone(original, subExchange, pairs, callback, false, true);
                        return;
                    }

                    total.incrementAndGet();

                    // maybe there are more processors to multicast
                    while (it.hasNext()) {

                        // prepare and run the next
                        ProcessorExchangePair pair = it.next();
                        subExchange = pair.getExchange();
                        updateNewExchange(subExchange, total.get(), pairs, it);
                        boolean sync = doProcessSequential(original, result, pairs, it, pair, callback, total);

                        if (!sync) {
                            LOG.trace("Processing exchangeId: {} is continued being processed asynchronously", original.getExchangeId());
                            return;
                        }

                        // Decide whether to continue with the multicast or not; similar logic to the Pipeline
                        // remember to test for stop on exception and aggregate before copying back results
                        continueProcessing = PipelineHelper.continueProcessing(subExchange, "Sequential processing failed for number " + total.get(), LOG);
                        if (stopOnException && !continueProcessing) {
                            if (subExchange.getException() != null) {
                                // wrap in exception to explain where it failed
                                subExchange.setException(new CamelExchangeException("Sequential processing failed for number " + total, subExchange, subExchange.getException()));
                            } else {
                                // we want to stop on exception, and the exception was handled by the error handler
                                // this is similar to what the pipeline does, so we should do the same to not surprise end users
                                // so we should set the failed exchange as the result and be done
                                result.set(subExchange);
                            }
                            // and do the done work
                            doDone(original, subExchange, pairs, callback, false, true);
                            return;
                        }

                        // must catch any exceptions from aggregation
                        try {
                            if (parallelAggregate) {
                                doAggregateInternal(getAggregationStrategy(subExchange), result, subExchange);
                            } else {
                                doAggregate(getAggregationStrategy(subExchange), result, subExchange);
                            }
                        } catch (Throwable e) {
                            // wrap in exception to explain where it failed
                            subExchange.setException(new CamelExchangeException("Sequential processing failed for number " + total, subExchange, e));
                            // and do the done work
                            doDone(original, subExchange, pairs, callback, false, true);
                            return;
                        }

                        total.incrementAndGet();
                    }

                    // do the done work
                    subExchange = result.get() != null ? result.get() : null;
                    doDone(original, subExchange, pairs, callback, false, true);
                }
            });
        } finally {
            // pop the block so by next round we have the same staring point and thus the tracing looks accurate
            if (traced != null) {
                traced.popBlock();
            }
        }

        return sync;
    }

    private void doProcessParallel(final ProcessorExchangePair pair) throws Exception {
        final Exchange exchange = pair.getExchange();
        Processor processor = pair.getProcessor();
        Producer producer = pair.getProducer();

        TracedRouteNodes traced = exchange.getUnitOfWork() != null ? exchange.getUnitOfWork().getTracedRouteNodes() : null;

        // compute time taken if sending to another endpoint
        StopWatch watch = null;
        try {
            // prepare tracing starting from a new block
            if (traced != null) {
                traced.pushBlock();
            }

            if (producer != null) {
                boolean sending = EventHelper.notifyExchangeSending(exchange.getContext(), exchange, producer.getEndpoint());
                if (sending) {
                    watch = new StopWatch();
                }
            }
            // let the prepared process it, remember to begin the exchange pair
            AsyncProcessor async = AsyncProcessorConverterHelper.convert(processor);
            pair.begin();
            // we invoke it synchronously as parallel async routing is too hard
            AsyncProcessorHelper.process(async, exchange);
        } finally {
            pair.done();
            // pop the block so by next round we have the same staring point and thus the tracing looks accurate
            if (traced != null) {
                traced.popBlock();
            }
            if (producer != null && watch != null) {
                Endpoint endpoint = producer.getEndpoint();
                long timeTaken = watch.taken();
                // emit event that the exchange was sent to the endpoint
                // this is okay to do here in the finally block, as the processing is not using the async routing engine
                //( we invoke it synchronously as parallel async routing is too hard)
                EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
            }
        }
    }

    /**
     * Common work which must be done when we are done multicasting.
     * <p/>
     * This logic applies for both running synchronous and asynchronous as there are multiple exist points
     * when using the asynchronous routing engine. And therefore we want the logic in one method instead
     * of being scattered.
     *
     * @param original     the original exchange
     * @param subExchange  the current sub exchange, can be <tt>null</tt> for the synchronous part
     * @param pairs        the pairs with the exchanges to process
     * @param callback     the callback
     * @param doneSync     the <tt>doneSync</tt> parameter to call on callback
     * @param forceExhaust whether or not error handling is exhausted
     */
    protected void doDone(Exchange original, Exchange subExchange, final Iterable<ProcessorExchangePair> pairs,
                          AsyncCallback callback, boolean doneSync, boolean forceExhaust) {

        // we are done so close the pairs iterator
        if (pairs instanceof Closeable) {
            IOHelper.close((Closeable) pairs, "pairs", LOG);
        }

        AggregationStrategy strategy = getAggregationStrategy(subExchange);
        if (strategy instanceof DelegateAggregationStrategy) {
            strategy = ((DelegateAggregationStrategy) strategy).getDelegate();
        }
        // invoke the on completion callback
        if (strategy instanceof CompletionAwareAggregationStrategy) {
            ((CompletionAwareAggregationStrategy) strategy).onCompletion(subExchange);
        }

        // cleanup any per exchange aggregation strategy
        removeAggregationStrategyFromExchange(original);

        // we need to know if there was an exception, and if the stopOnException option was enabled
        // also we would need to know if any error handler has attempted redelivery and exhausted
        boolean stoppedOnException = false;
        boolean exception = false;
        boolean exhaust = forceExhaust || subExchange != null && (subExchange.getException() != null || ExchangeHelper.isRedeliveryExhausted(subExchange));
        if (original.getException() != null || subExchange != null && subExchange.getException() != null) {
            // there was an exception and we stopped
            stoppedOnException = isStopOnException();
            exception = true;
        }

        // must copy results at this point
        if (subExchange != null) {
            if (stoppedOnException) {
                // if we stopped due an exception then only propagate the exception
                original.setException(subExchange.getException());
            } else {
                // copy the current result to original so it will contain this result of this eip
                ExchangeHelper.copyResults(original, subExchange);
            }
        }

        // .. and then if there was an exception we need to configure the redelivery exhaust
        // for example the noErrorHandler will not cause redelivery exhaust so if this error
        // handled has been in use, then the exhaust would be false (if not forced)
        if (exception) {
            // multicast uses error handling on its output processors and they have tried to redeliver
            // so we shall signal back to the other error handlers that we are exhausted and they should not
            // also try to redeliver as we will then do that twice
            original.setProperty(Exchange.REDELIVERY_EXHAUSTED, exhaust);
        }

        callback.done(doneSync);
    }

    /**
     * Aggregate the {@link Exchange} with the current result.
     * This method is synchronized and is called directly when parallelAggregate is disabled (by default).
     *
     * @param strategy the aggregation strategy to use
     * @param result   the current result
     * @param exchange the exchange to be added to the result
     * @see #doAggregateInternal(org.apache.camel.processor.aggregate.AggregationStrategy, org.apache.camel.util.concurrent.AtomicExchange, org.apache.camel.Exchange)
     */
    protected synchronized void doAggregate(AggregationStrategy strategy, AtomicExchange result, Exchange exchange) {
        doAggregateInternal(strategy, result, exchange);
    }

    /**
     * Aggregate the {@link Exchange} with the current result.
     * This method is unsynchronized and is called directly when parallelAggregate is enabled.
     * In all other cases, this method is called from the doAggregate which is a synchronized method
     *
     * @param strategy the aggregation strategy to use
     * @param result   the current result
     * @param exchange the exchange to be added to the result
     * @see #doAggregate(org.apache.camel.processor.aggregate.AggregationStrategy, org.apache.camel.util.concurrent.AtomicExchange, org.apache.camel.Exchange)
     */
    protected void doAggregateInternal(AggregationStrategy strategy, AtomicExchange result, Exchange exchange) {
        if (strategy != null) {
            // prepare the exchanges for aggregation
            Exchange oldExchange = result.get();
            ExchangeHelper.prepareAggregation(oldExchange, exchange);
            result.set(strategy.aggregate(oldExchange, exchange));
        }
    }

    protected void updateNewExchange(Exchange exchange, int index, Iterable<ProcessorExchangePair> allPairs,
                                     Iterator<ProcessorExchangePair> it) {
        exchange.setProperty(Exchange.MULTICAST_INDEX, index);
        if (it.hasNext()) {
            exchange.setProperty(Exchange.MULTICAST_COMPLETE, Boolean.FALSE);
        } else {
            exchange.setProperty(Exchange.MULTICAST_COMPLETE, Boolean.TRUE);
        }
    }

    protected Integer getExchangeIndex(Exchange exchange) {
        return exchange.getProperty(Exchange.MULTICAST_INDEX, Integer.class);
    }

    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange) throws Exception {
        List<ProcessorExchangePair> result = new ArrayList<ProcessorExchangePair>(processors.size());

        StreamCache streamCache = null;
        if (isParallelProcessing() && exchange.getIn().getBody() instanceof StreamCache) {
            // in parallel processing case, the stream must be copied, therefore get the stream
            streamCache = (StreamCache) exchange.getIn().getBody();
        }

        int index = 0;
        for (Processor processor : processors) {
            // copy exchange, and do not share the unit of work
            Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);

            if (streamCache != null) {
                if (index > 0) {
                    // copy it otherwise parallel processing is not possible,
                    // because streams can only be read once
                    StreamCache copiedStreamCache = streamCache.copy(copy);
                    if (copiedStreamCache != null) {
                        copy.getIn().setBody(copiedStreamCache);
                    }
                }
            }

            // If the multi-cast processor has an aggregation strategy
            // then the StreamCache created by the child routes must not be 
            // closed by the unit of work of the child route, but by the unit of 
            // work of the parent route or grand parent route or grand grand parent route ...(in case of nesting).
            // Set therefore the unit of work of the  parent route as stream cache unit of work, 
            // if it is not already set.
            if (copy.getProperty(Exchange.STREAM_CACHE_UNIT_OF_WORK) == null) {
                copy.setProperty(Exchange.STREAM_CACHE_UNIT_OF_WORK, exchange.getUnitOfWork());
            }
            // if we share unit of work, we need to prepare the child exchange
            if (isShareUnitOfWork()) {
                prepareSharedUnitOfWork(copy, exchange);
            }

            // and add the pair
            RouteContext routeContext = exchange.getUnitOfWork() != null ? exchange.getUnitOfWork().getRouteContext() : null;
            result.add(createProcessorExchangePair(index++, processor, copy, routeContext));
        }

        if (exchange.getException() != null) {
            // force any exceptions occurred during creation of exchange paris to be thrown
            // before returning the answer;
            throw exchange.getException();
        }

        return result;
    }

    /**
     * Creates the {@link ProcessorExchangePair} which holds the processor and exchange to be send out.
     * <p/>
     * You <b>must</b> use this method to create the instances of {@link ProcessorExchangePair} as they
     * need to be specially prepared before use.
     *
     * @param index        the index
     * @param processor    the processor
     * @param exchange     the exchange
     * @param routeContext the route context
     * @return prepared for use
     */
    protected ProcessorExchangePair createProcessorExchangePair(int index, Processor processor, Exchange exchange,
                                                                RouteContext routeContext) {
        Processor prepared = processor;

        // set property which endpoint we send to
        setToEndpoint(exchange, prepared);

        // rework error handling to support fine grained error handling
        prepared = createErrorHandler(routeContext, exchange, prepared);

        // invoke on prepare on the exchange if specified
        if (onPrepare != null) {
            try {
                onPrepare.process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }
        }
        return new DefaultProcessorExchangePair(index, processor, prepared, exchange);
    }

    protected Processor createErrorHandler(RouteContext routeContext, Exchange exchange, Processor processor) {
        Processor answer;

        boolean tryBlock = exchange.getProperty(Exchange.TRY_ROUTE_BLOCK, false, boolean.class);

        // do not wrap in error handler if we are inside a try block
        if (!tryBlock && routeContext != null) {
            // wrap the producer in error handler so we have fine grained error handling on
            // the output side instead of the input side
            // this is needed to support redelivery on that output alone and not doing redelivery
            // for the entire multicast block again which will start from scratch again

            // create key for cache
            final PreparedErrorHandler key = new PreparedErrorHandler(routeContext, processor);

            // lookup cached first to reuse and preserve memory
            answer = errorHandlers.get(key);
            if (answer != null) {
                LOG.trace("Using existing error handler for: {}", processor);
                return answer;
            }

            LOG.trace("Creating error handler for: {}", processor);
            ErrorHandlerFactory builder = routeContext.getRoute().getErrorHandlerBuilder();
            // create error handler (create error handler directly to keep it light weight,
            // instead of using ProcessorDefinition.wrapInErrorHandler)
            try {
                processor = builder.createErrorHandler(routeContext, processor);

                // and wrap in unit of work processor so the copy exchange also can run under UoW
                answer = createUnitOfWorkProcessor(routeContext, processor, exchange);

                boolean child = exchange.getProperty(Exchange.PARENT_UNIT_OF_WORK, UnitOfWork.class) != null;

                // must start the error handler
                ServiceHelper.startServices(answer);

                // here we don't cache the child unit of work
                if (!child) {
                    // add to cache
                    errorHandlers.putIfAbsent(key, answer);
                }

            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        } else {
            // and wrap in unit of work processor so the copy exchange also can run under UoW
            answer = createUnitOfWorkProcessor(routeContext, processor, exchange);
        }

        return answer;
    }

    /**
     * Strategy to create the unit of work to be used for the sub route
     *
     * @param routeContext the route context
     * @param processor    the processor
     * @param exchange     the exchange
     * @return the unit of work processor
     */
    protected Processor createUnitOfWorkProcessor(RouteContext routeContext, Processor processor, Exchange exchange) {
        CamelInternalProcessor internal = new CamelInternalProcessor(processor);

        // and wrap it in a unit of work so the UoW is on the top, so the entire route will be in the same UoW
        UnitOfWork parent = exchange.getProperty(Exchange.PARENT_UNIT_OF_WORK, UnitOfWork.class);
        if (parent != null) {
            internal.addAdvice(new CamelInternalProcessor.ChildUnitOfWorkProcessorAdvice(routeContext, parent));
        } else {
            internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(routeContext));
        }

        return internal;
    }

    /**
     * Prepares the exchange for participating in a shared unit of work
     * <p/>
     * This ensures a child exchange can access its parent {@link UnitOfWork} when it participate
     * in a shared unit of work.
     *
     * @param childExchange  the child exchange
     * @param parentExchange the parent exchange
     */
    protected void prepareSharedUnitOfWork(Exchange childExchange, Exchange parentExchange) {
        childExchange.setProperty(Exchange.PARENT_UNIT_OF_WORK, parentExchange.getUnitOfWork());
    }

    protected void doStart() throws Exception {
        if (isParallelProcessing() && executorService == null) {
            throw new IllegalArgumentException("ParallelProcessing is enabled but ExecutorService has not been set");
        }
        if (timeout > 0 && !isParallelProcessing()) {
            throw new IllegalArgumentException("Timeout is used but ParallelProcessing has not been enabled");
        }
        if (isParallelProcessing() && aggregateExecutorService == null) {
            // use unbounded thread pool so we ensure the aggregate on-the-fly task always will have assigned a thread
            // and run the tasks when the task is submitted. If not then the aggregate task may not be able to run
            // and signal completion during processing, which would lead to what would appear as a dead-lock or a slow processing
            String name = getClass().getSimpleName() + "-AggregateTask";
            aggregateExecutorService = createAggregateExecutorService(name);
        }
        if (aggregationStrategy instanceof CamelContextAware) {
            ((CamelContextAware) aggregationStrategy).setCamelContext(camelContext);
        }

        ServiceHelper.startServices(aggregationStrategy, processors);
    }

    /**
     * Strategy to create the thread pool for the aggregator background task which waits for and aggregates
     * completed tasks when running in parallel mode.
     *
     * @param name  the suggested name for the background thread
     * @return the thread pool
     */
    protected synchronized ExecutorService createAggregateExecutorService(String name) {
        // use a cached thread pool so we each on-the-fly task has a dedicated thread to process completions as they come in
        return camelContext.getExecutorServiceManager().newCachedThreadPool(this, name);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(processors, errorHandlers, aggregationStrategy);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processors, errorHandlers, aggregationStrategy);
        // only clear error handlers when shutting down
        errorHandlers.clear();

        if (shutdownExecutorService && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
        }
        if (aggregateExecutorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(aggregateExecutorService);
        }
    }

    protected static void setToEndpoint(Exchange exchange, Processor processor) {
        if (processor instanceof Producer) {
            Producer producer = (Producer) processor;
            exchange.setProperty(Exchange.TO_ENDPOINT, producer.getEndpoint().getEndpointUri());
        }
    }

    protected AggregationStrategy getAggregationStrategy(Exchange exchange) {
        AggregationStrategy answer = null;

        // prefer to use per Exchange aggregation strategy over a global strategy
        if (exchange != null) {
            Map<?, ?> property = exchange.getProperty(Exchange.AGGREGATION_STRATEGY, Map.class);
            Map<Object, AggregationStrategy> map = CastUtils.cast(property);
            if (map != null) {
                answer = map.get(this);
            }
        }
        if (answer == null) {
            // fallback to global strategy
            answer = getAggregationStrategy();
        }
        return answer;
    }

    /**
     * Sets the given {@link org.apache.camel.processor.aggregate.AggregationStrategy} on the {@link Exchange}.
     *
     * @param exchange            the exchange
     * @param aggregationStrategy the strategy
     */
    protected void setAggregationStrategyOnExchange(Exchange exchange, AggregationStrategy aggregationStrategy) {
        Map<?, ?> property = exchange.getProperty(Exchange.AGGREGATION_STRATEGY, Map.class);
        Map<Object, AggregationStrategy> map = CastUtils.cast(property);
        if (map == null) {
            map = new ConcurrentHashMap<Object, AggregationStrategy>();
        } else {
            // it is not safe to use the map directly as the exchange doesn't have the deep copy of it's properties
            // we just create a new copy if we need to change the map
            map = new ConcurrentHashMap<Object, AggregationStrategy>(map);
        }
        // store the strategy using this processor as the key
        // (so we can store multiple strategies on the same exchange)
        map.put(this, aggregationStrategy);
        exchange.setProperty(Exchange.AGGREGATION_STRATEGY, map);
    }

    /**
     * Removes the associated {@link org.apache.camel.processor.aggregate.AggregationStrategy} from the {@link Exchange}
     * which must be done after use.
     *
     * @param exchange the current exchange
     */
    protected void removeAggregationStrategyFromExchange(Exchange exchange) {
        Map<?, ?> property = exchange.getProperty(Exchange.AGGREGATION_STRATEGY, Map.class);
        Map<Object, AggregationStrategy> map = CastUtils.cast(property);
        if (map == null) {
            return;
        }
        // remove the strategy using this processor as the key
        map.remove(this);
    }

    /**
     * Is the multicast processor working in streaming mode?
     * <p/>
     * In streaming mode:
     * <ul>
     * <li>we use {@link Iterable} to ensure we can send messages as soon as the data becomes available</li>
     * <li>for parallel processing, we start aggregating responses as they get send back to the processor;
     * this means the {@link org.apache.camel.processor.aggregate.AggregationStrategy} has to take care of handling out-of-order arrival of exchanges</li>
     * </ul>
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Should the multicast processor stop processing further exchanges in case of an exception occurred?
     */
    public boolean isStopOnException() {
        return stopOnException;
    }

    /**
     * Returns the producers to multicast to
     */
    public Collection<Processor> getProcessors() {
        return processors;
    }

    /**
     * An optional timeout in millis when using parallel processing
     */
    public long getTimeout() {
        return timeout;
    }

    /**
     * Use {@link #getAggregationStrategy(org.apache.camel.Exchange)} instead.
     */
    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public boolean isParallelProcessing() {
        return parallelProcessing;
    }

    public boolean isParallelAggregate() {
        return parallelAggregate;
    }

    public boolean isStopOnAggregateException() {
        return stopOnAggregateException;
    }

    public boolean isShareUnitOfWork() {
        return shareUnitOfWork;
    }

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        return new ArrayList<Processor>(processors);
    }

    public boolean hasNext() {
        return processors != null && !processors.isEmpty();
    }
}
