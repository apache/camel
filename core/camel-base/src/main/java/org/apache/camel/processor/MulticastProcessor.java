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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.EventHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.CastUtils;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.KeyValueHolder;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.concurrent.AsyncCompletionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements the Multicast pattern to send a message exchange to a number of
 * endpoints, each endpoint receiving a copy of the message exchange.
 *
 * @see Pipeline
 */
public class MulticastProcessor extends AsyncProcessorSupport implements Navigate<Processor>, Traceable, IdAware, RouteIdAware {

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

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public Exchange getExchange() {
            return exchange;
        }

        @Override
        public Producer getProducer() {
            if (processor instanceof Producer) {
                return (Producer) processor;
            }
            return null;
        }

        @Override
        public Processor getProcessor() {
            return prepared;
        }

        @Override
        public void begin() {
            // noop
        }

        @Override
        public void done() {
            // noop
        }

    }

    /**
     * Class that represents prepared fine grained error handlers when processing multicasted/splitted exchanges
     * <p/>
     * See the <tt>createProcessorExchangePair</tt> and <tt>createErrorHandler</tt> methods.
     */
    static final class ErrorHandlerKey extends KeyValueHolder<Route, Processor> {

        ErrorHandlerKey(Route key, Processor value) {
            super(key, value);
        }

    }

    protected final Processor onPrepare;
    private final CamelContext camelContext;
    private final Route route;
    private final ReactiveExecutor reactiveExecutor;
    private String id;
    private String routeId;
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
    private boolean shutdownAggregateExecutorService;
    private final long timeout;
    private final ConcurrentMap<ErrorHandlerKey, Processor> errorHandlers = new ConcurrentHashMap<>();
    private final boolean shareUnitOfWork;

    public MulticastProcessor(CamelContext camelContext, Route route, Collection<Processor> processors) {
        this(camelContext, route, processors, null);
    }

    public MulticastProcessor(CamelContext camelContext, Route route, Collection<Processor> processors, AggregationStrategy aggregationStrategy) {
        this(camelContext, route, processors, aggregationStrategy, false, null, false, false, false, 0, null, false, false);
    }

    public MulticastProcessor(CamelContext camelContext, Route route, Collection<Processor> processors, AggregationStrategy aggregationStrategy, boolean parallelProcessing,
                              ExecutorService executorService, boolean shutdownExecutorService, boolean streaming, boolean stopOnException, long timeout, Processor onPrepare,
                              boolean shareUnitOfWork, boolean parallelAggregate) {
        this(camelContext, route, processors, aggregationStrategy, parallelProcessing, executorService, shutdownExecutorService, streaming, stopOnException, timeout, onPrepare,
             shareUnitOfWork, parallelAggregate, false);
    }
    
    public MulticastProcessor(CamelContext camelContext, Route route, Collection<Processor> processors, AggregationStrategy aggregationStrategy,
                              boolean parallelProcessing, ExecutorService executorService, boolean shutdownExecutorService, boolean streaming,
                              boolean stopOnException, long timeout, Processor onPrepare, boolean shareUnitOfWork,
                              boolean parallelAggregate, boolean stopOnAggregateException) {
        notNull(camelContext, "camelContext");
        this.camelContext = camelContext;
        this.route = route;
        this.reactiveExecutor = camelContext.adapt(ExtendedCamelContext.class).getReactiveExecutor();
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
        return id;
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

    @Override
    public String getTraceLabel() {
        return "multicast";
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    protected void doInit() throws Exception {
        if (route != null) {
            Exchange exchange = new DefaultExchange(getCamelContext());
            for (Processor processor : getProcessors()) {
                createErrorHandler(route, exchange, processor);
            }
        }
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Iterable<ProcessorExchangePair> pairs;
        try {
            pairs = createProcessorExchangePairs(exchange);
        } catch (Throwable e) {
            exchange.setException(e);
            // unexpected exception was thrown, maybe from iterator etc. so do not regard as exhausted
            // and do the done work
            doDone(exchange, null, null, callback, true, false);
            return true;
        }

        MulticastState state = new MulticastState(exchange, pairs, callback);
        if (isParallelProcessing()) {
            executorService.submit(() -> reactiveExecutor.schedule(state));
        } else {
            if (exchange.isTransacted()) {
                reactiveExecutor.scheduleSync(state);
            } else {
                reactiveExecutor.scheduleMain(state);
            }
        }

        // the remainder of the multicast will be completed async
        // so we break out now, then the callback will be invoked which then
        // continue routing from where we left here
        return false;
    }

    protected void schedule(Runnable runnable) {
        if (isParallelProcessing()) {
            executorService.submit(() -> reactiveExecutor.schedule(runnable));
        } else {
            reactiveExecutor.schedule(runnable);
        }
    }

    protected class MulticastState implements Runnable {

        final Exchange original;
        final Iterable<ProcessorExchangePair> pairs;
        final AsyncCallback callback;
        final Iterator<ProcessorExchangePair> iterator;
        final ReentrantLock lock;
        final AsyncCompletionService<Exchange> completion;
        final AtomicReference<Exchange> result;
        final AtomicInteger nbExchangeSent = new AtomicInteger();
        final AtomicInteger nbAggregated = new AtomicInteger();
        final AtomicBoolean allSent = new AtomicBoolean();
        final AtomicBoolean done = new AtomicBoolean();

        MulticastState(Exchange original, Iterable<ProcessorExchangePair> pairs, AsyncCallback callback) {
            this.original = original;
            this.pairs = pairs;
            this.callback = callback;
            this.iterator = pairs.iterator();
            this.lock = new ReentrantLock();
            this.completion = new AsyncCompletionService<>(MulticastProcessor.this::schedule, !isStreaming(), lock);
            this.result = new AtomicReference<>();
            if (timeout > 0) {
                schedule(aggregateExecutorService, this::timeout, timeout, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public String toString() {
            return "MulticastTask";
        }

        @Override
        public void run() {
            try {
                if (done.get()) {
                    return;
                }

                // Check if the iterator is empty
                // This can happen the very first time we check the existence
                // of an item before queuing the run.
                // or some iterators may return true for hasNext() but then null in next()
                if (!iterator.hasNext()) {
                    doDone(result.get(), true);
                    return;
                }

                ProcessorExchangePair pair = iterator.next();
                boolean hasNext = iterator.hasNext();
                // some iterators may return true for hasNext() but then null in next()
                if (pair == null && !hasNext) {
                    doDone(result.get(), true);
                    return;
                }

                Exchange exchange = pair.getExchange();
                int index = nbExchangeSent.getAndIncrement();
                updateNewExchange(exchange, index, pairs, hasNext);

                // Schedule the processing of the next pair
                if (hasNext) {
                    if (isParallelProcessing()) {
                        schedule(this);
                    }
                } else {
                    allSent.set(true);
                }

                completion.submit(exchangeResult -> {
                    // compute time taken if sending to another endpoint
                    StopWatch watch = beforeSend(pair);

                    AsyncProcessor async = AsyncProcessorConverterHelper.convert(pair.getProcessor());
                    async.process(exchange, doneSync -> {
                        afterSend(pair, watch);

                        // Decide whether to continue with the multicast or not; similar logic to the Pipeline
                        // remember to test for stop on exception and aggregate before copying back results
                        boolean continueProcessing = PipelineHelper.continueProcessing(exchange, "Multicast processing failed for number " + index, LOG);
                        if (stopOnException && !continueProcessing) {
                            if (exchange.getException() != null) {
                                // wrap in exception to explain where it failed
                                exchange.setException(new CamelExchangeException("Multicast processing failed for number " + index, exchange, exchange.getException()));
                            } else {
                                // we want to stop on exception, and the exception was handled by the error handler
                                // this is similar to what the pipeline does, so we should do the same to not surprise end users
                                // so we should set the failed exchange as the result and be done
                                result.set(exchange);
                            }
                            // and do the done work
                            doDone(exchange, true);
                            return;
                        }

                        exchangeResult.accept(exchange);

                        // aggregate exchanges if any
                        aggregate();

                        // next step
                        if (hasNext && !isParallelProcessing()) {
                            schedule(this);
                        }
                    });
                });
            } catch (Exception e) {
                original.setException(e);
                doDone(null, false);
            }
        }

        protected void aggregate() {
            Lock lock = this.lock;
            if (lock.tryLock()) {
                try {
                    Exchange exchange;
                    while (!done.get() && (exchange = completion.poll()) != null) {
                        doAggregate(result, exchange, original);
                        if (nbAggregated.incrementAndGet() >= nbExchangeSent.get() && allSent.get()) {
                            doDone(result.get(), true);
                        }
                    }
                } catch (Throwable e) {
                    original.setException(e);
                    // and do the done work
                    doDone(null, false);
                } finally {
                    lock.unlock();
                }
            }
        }

        protected void timeout() {
            Lock lock = this.lock;
            if (lock.tryLock()) {
                try {
                    while (nbAggregated.get() < nbExchangeSent.get()) {
                        Exchange exchange = completion.pollUnordered();
                        int index = exchange != null ? getExchangeIndex(exchange) : nbExchangeSent.get();
                        while (nbAggregated.get() < index) {
                            AggregationStrategy strategy = getAggregationStrategy(null);
                            strategy.timeout(result.get() != null ? result.get() : original,
                                    nbAggregated.getAndIncrement(), nbExchangeSent.get(), timeout);
                        }
                        if (exchange != null) {
                            doAggregate(result, exchange, original);
                            nbAggregated.incrementAndGet();
                        }
                    }
                    doDone(result.get(), true);
                } catch (Throwable e) {
                    original.setException(e);
                    // and do the done work
                    doDone(null, false);
                } finally {
                    lock.unlock();
                }
            }
        }

        protected void doDone(Exchange exchange, boolean forceExhaust) {
            if (done.compareAndSet(false, true)) {
                MulticastProcessor.this.doDone(original, exchange, pairs, callback, false, forceExhaust);
            }
        }
    }

    protected void schedule(Executor executor, Runnable runnable, long delay, TimeUnit unit) {
        if (executor instanceof ScheduledExecutorService) {
            ((ScheduledExecutorService) executor).schedule(runnable, delay, unit);
        } else {
            executor.execute(() -> {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // ignore
                }
                runnable.run();
            });
        }
    }

    protected StopWatch beforeSend(ProcessorExchangePair pair) {
        StopWatch watch;
        final Exchange e = pair.getExchange();
        final Producer p = pair.getProducer();
        if (p != null) {
            boolean sending = EventHelper.notifyExchangeSending(e.getContext(), e, p.getEndpoint());
            if (sending) {
                watch = new StopWatch();
            } else {
                watch = null;
            }
        } else {
            watch = null;
        }

        // let the prepared process it, remember to begin the exchange pair
        pair.begin();

        // return the watch
        return watch;
    }

    protected void afterSend(ProcessorExchangePair pair, StopWatch watch) {
        // we are done with the exchange pair
        pair.done();

        // okay we are done, so notify the exchange was sent
        final Producer producer = pair.getProducer();
        if (producer != null && watch != null) {
            long timeTaken = watch.taken();
            final Exchange e = pair.getExchange();
            Endpoint endpoint = producer.getEndpoint();
            // emit event that the exchange was sent to the endpoint
            EventHelper.notifyExchangeSent(e.getContext(), e, endpoint, timeTaken);
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
        // invoke the on completion callback
        if (strategy != null) {
            strategy.onCompletion(subExchange);
        }

        // cleanup any per exchange aggregation strategy
        removeAggregationStrategyFromExchange(original);

        // we need to know if there was an exception, and if the stopOnException option was enabled
        // also we would need to know if any error handler has attempted redelivery and exhausted
        boolean stoppedOnException = false;
        boolean exception = false;
        ExtendedExchange see = (ExtendedExchange) subExchange;
        boolean exhaust = forceExhaust || see != null && (see.getException() != null || see.isRedeliveryExhausted());
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
            // also try to redeliver as we would then do that twice
            original.adapt(ExtendedExchange.class).setRedeliveryExhausted(exhaust);
        }

        reactiveExecutor.schedule(callback);
    }

    /**
     * Aggregate the {@link Exchange} with the current result.
     * This method is synchronized and is called directly when parallelAggregate is disabled (by default).
     *
     * @param result   the current result
     * @param exchange the exchange to be added to the result
     * @param inputExchange the input exchange that was sent as input to this EIP
     */
    protected void doAggregate(AtomicReference<Exchange> result, Exchange exchange, Exchange inputExchange) {
        if (parallelAggregate) {
            doAggregateInternal(getAggregationStrategy(exchange), result, exchange, inputExchange);
        } else {
            doAggregateSync(getAggregationStrategy(exchange), result, exchange, inputExchange);
        }
    }

    /**
     * Aggregate the {@link Exchange} with the current result.
     * This method is synchronized and is called directly when parallelAggregate is disabled (by default).
     *
     * @param strategy the aggregation strategy to use
     * @param result   the current result
     * @param exchange the exchange to be added to the result
     * @param inputExchange the input exchange that was sent as input to this EIP
     */
    private synchronized void doAggregateSync(AggregationStrategy strategy, AtomicReference<Exchange> result, Exchange exchange, Exchange inputExchange) {
        doAggregateInternal(strategy, result, exchange, inputExchange);
    }

    /**
     * Aggregate the {@link Exchange} with the current result.
     * This method is unsynchronized and is called directly when parallelAggregate is enabled.
     * In all other cases, this method is called from the doAggregate which is a synchronized method
     *
     * @param strategy the aggregation strategy to use
     * @param result   the current result
     * @param exchange the exchange to be added to the result
     * @param inputExchange the input exchange that was sent as input to this EIP
     */
    private void doAggregateInternal(AggregationStrategy strategy, AtomicReference<Exchange> result, Exchange exchange, Exchange inputExchange) {
        if (strategy != null) {
            // prepare the exchanges for aggregation
            Exchange oldExchange = result.get();
            ExchangeHelper.prepareAggregation(oldExchange, exchange);
            result.set(strategy.aggregate(oldExchange, exchange, inputExchange));
        }
    }

    protected void updateNewExchange(Exchange exchange, int index, Iterable<ProcessorExchangePair> allPairs, boolean hasNext) {
        exchange.setProperty(Exchange.MULTICAST_INDEX, index);
        if (hasNext) {
            exchange.setProperty(Exchange.MULTICAST_COMPLETE, Boolean.FALSE);
        } else {
            exchange.setProperty(Exchange.MULTICAST_COMPLETE, Boolean.TRUE);
        }
    }

    protected Integer getExchangeIndex(Exchange exchange) {
        return exchange.getProperty(Exchange.MULTICAST_INDEX, Integer.class);
    }

    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange) throws Exception {
        List<ProcessorExchangePair> result = new ArrayList<>(processors.size());

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
            Route route = ExchangeHelper.getRoute(exchange);
            result.add(createProcessorExchangePair(index++, processor, copy, route));
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
     * @param route the route context
     * @return prepared for use
     */
    protected ProcessorExchangePair createProcessorExchangePair(int index, Processor processor, Exchange exchange,
                                                                Route route) {
        Processor prepared = processor;

        // set property which endpoint we send to
        setToEndpoint(exchange, prepared);

        // rework error handling to support fine grained error handling
        prepared = createErrorHandler(route, exchange, prepared);

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

    protected Processor createErrorHandler(Route route, Exchange exchange, Processor processor) {
        Processor answer;

        if (route != this.route && this.route != null) {
            throw new UnsupportedOperationException("Is this really correct ?");
        }
        boolean tryBlock = exchange.getProperty(Exchange.TRY_ROUTE_BLOCK, false, boolean.class);

        // do not wrap in error handler if we are inside a try block
        if (!tryBlock && route != null) {
            // wrap the producer in error handler so we have fine grained error handling on
            // the output side instead of the input side
            // this is needed to support redelivery on that output alone and not doing redelivery
            // for the entire multicast block again which will start from scratch again

            // create key for cache
            final ErrorHandlerKey key = new ErrorHandlerKey(route, processor);

            // lookup cached first to reuse and preserve memory
            answer = errorHandlers.get(key);
            if (answer != null) {
                LOG.trace("Using existing error handler for: {}", processor);
                return answer;
            }

            LOG.trace("Creating error handler for: {}", processor);
            // create error handler (create error handler directly to keep it light weight,
            // instead of using ProcessorReifier.wrapInErrorHandler)
            try {
                processor = route.createErrorHandler(processor);

                // and wrap in unit of work processor so the copy exchange also can run under UoW
                answer = createUnitOfWorkProcessor(route, processor, exchange);

                boolean child = exchange.getProperty(Exchange.PARENT_UNIT_OF_WORK, UnitOfWork.class) != null;

                // must start the error handler
                ServiceHelper.startService(answer);

                // here we don't cache the child unit of work
                if (!child) {
                    // add to cache
                    errorHandlers.putIfAbsent(key, answer);
                }

            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        } else {
            // and wrap in unit of work processor so the copy exchange also can run under UoW
            answer = createUnitOfWorkProcessor(route, processor, exchange);
        }

        return answer;
    }

    /**
     * Strategy to create the unit of work to be used for the sub route
     *
     * @param processor    the processor
     * @param exchange     the exchange
     * @return the unit of work processor
     */
    protected Processor createUnitOfWorkProcessor(Route route, Processor processor, Exchange exchange) {
        CamelInternalProcessor internal = new CamelInternalProcessor(exchange.getContext(), processor);

        // and wrap it in a unit of work so the UoW is on the top, so the entire route will be in the same UoW
        UnitOfWork parent = exchange.getProperty(Exchange.PARENT_UNIT_OF_WORK, UnitOfWork.class);
        if (parent != null) {
            internal.addAdvice(new CamelInternalProcessor.ChildUnitOfWorkProcessorAdvice(route, exchange.getContext(), parent));
        } else {
            internal.addAdvice(new CamelInternalProcessor.UnitOfWorkProcessorAdvice(route, exchange.getContext()));
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

    @Override
    protected void doStart() throws Exception {
        if (isParallelProcessing() && executorService == null) {
            throw new IllegalArgumentException("ParallelProcessing is enabled but ExecutorService has not been set");
        }
        if (timeout > 0 && aggregateExecutorService == null) {
            // use unbounded thread pool so we ensure the aggregate on-the-fly task always will have assigned a thread
            // and run the tasks when the task is submitted. If not then the aggregate task may not be able to run
            // and signal completion during processing, which would lead to what would appear as a dead-lock or a slow processing
            String name = getClass().getSimpleName() + "-AggregateTask";
            aggregateExecutorService = createAggregateExecutorService(name);
            shutdownAggregateExecutorService = true;
        }
        if (aggregationStrategy instanceof CamelContextAware) {
            ((CamelContextAware) aggregationStrategy).setCamelContext(camelContext);
        }

        ServiceHelper.startService(aggregationStrategy, processors);
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
        return camelContext.getExecutorServiceManager().newScheduledThreadPool(this, name, 0);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processors, errorHandlers, aggregationStrategy);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(processors, errorHandlers, aggregationStrategy);
        // only clear error handlers when shutting down
        errorHandlers.clear();

        if (shutdownExecutorService && executorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
        }
        if (shutdownAggregateExecutorService && aggregateExecutorService != null) {
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
     * Sets the given {@link AggregationStrategy} on the {@link Exchange}.
     *
     * @param exchange            the exchange
     * @param aggregationStrategy the strategy
     */
    protected void setAggregationStrategyOnExchange(Exchange exchange, AggregationStrategy aggregationStrategy) {
        Map<?, ?> property = exchange.getProperty(Exchange.AGGREGATION_STRATEGY, Map.class);
        Map<Object, AggregationStrategy> map = CastUtils.cast(property);
        if (map == null) {
            map = new ConcurrentHashMap<>();
        } else {
            // it is not safe to use the map directly as the exchange doesn't have the deep copy of it's properties
            // we just create a new copy if we need to change the map
            map = new ConcurrentHashMap<>(map);
        }
        // store the strategy using this processor as the key
        // (so we can store multiple strategies on the same exchange)
        map.put(this, aggregationStrategy);
        exchange.setProperty(Exchange.AGGREGATION_STRATEGY, map);
    }

    /**
     * Removes the associated {@link AggregationStrategy} from the {@link Exchange}
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
     * this means the {@link AggregationStrategy} has to take care of handling out-of-order arrival of exchanges</li>
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

    public ExecutorService getAggregateExecutorService() {
        return aggregateExecutorService;
    }

    public void setAggregateExecutorService(ExecutorService aggregateExecutorService) {
        this.aggregateExecutorService = aggregateExecutorService;
        // we use a custom executor so do not shutdown
        this.shutdownAggregateExecutorService = false;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        return new ArrayList<>(processors);
    }

    @Override
    public boolean hasNext() {
        return processors != null && !processors.isEmpty();
    }
}
