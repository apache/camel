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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TracedRouteNodes;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.concurrent.AtomicExchange;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
import org.apache.camel.util.concurrent.SubmitOrderedCompletionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements the Multicast pattern to send a message exchange to a number of
 * endpoints, each endpoint receiving a copy of the message exchange.
 *
 * @see Pipeline
 * @version $Revision$
 */
public class MulticastProcessor extends ServiceSupport implements Processor, Navigate<Processor>, Traceable {

    private static final int DEFAULT_THREADPOOL_SIZE = 10;
    private static final transient Log LOG = LogFactory.getLog(MulticastProcessor.class);

    /**
     * Class that represent each step in the multicast route to do
     */
    static class ProcessorExchangePair {
        private final Processor processor;
        private final Exchange exchange;

        public ProcessorExchangePair(Processor processor, Exchange exchange) {
            this.processor = processor;
            this.exchange = exchange;
        }

        public Processor getProcessor() {
            return processor;
        }

        public Exchange getExchange() {
            return exchange;
        }
    }

    private Collection<Processor> processors;
    private final AggregationStrategy aggregationStrategy;
    private final boolean isParallelProcessing;
    private final boolean streaming;
    private final boolean stopOnException;
    private ExecutorService executorService;
    private Channel channel;

    public MulticastProcessor(Collection<Processor> processors) {
        this(processors, null);
    }

    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy) {
        this(processors, aggregationStrategy, false, null, false, false);
    }
    
    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy,
                              boolean parallelProcessing, ExecutorService executorService, boolean streaming, boolean stopOnException) {
        notNull(processors, "processors");
        this.processors = processors;
        this.aggregationStrategy = aggregationStrategy;
        this.isParallelProcessing = parallelProcessing;
        this.executorService = executorService;
        this.streaming = streaming;
        this.stopOnException = stopOnException;

        if (isParallelProcessing()) {
            if (this.executorService == null) {
                // setup default executor as parallel processing requires an executor
                this.executorService = ExecutorServiceHelper.newScheduledThreadPool(DEFAULT_THREADPOOL_SIZE, "Multicast", true);
            }
        }
    }

    @Override
    public String toString() {
        return "Multicast[" + getProcessors() + "]";
    }

    public String getTraceLabel() {
        return "multicast";
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public void process(Exchange exchange) throws Exception {
        final AtomicExchange result = new AtomicExchange();
        final Iterable<ProcessorExchangePair> pairs = createProcessorExchangePairs(exchange);

        // multicast uses fine grained error handling on the output processors
        // so use try .. catch to cater for this
        try {
            if (isParallelProcessing()) {
                doProcessParallel(result, pairs, isStreaming());
            } else {
                doProcessSequential(result, pairs);
            }

            if (result.get() != null) {
                ExchangeHelper.copyResults(exchange, result.get());
            }
        } catch (Exception e) {
            // multicast uses error handling on its output processors and they have tried to redeliver
            // so we shall signal back to the other error handlers that we are exhausted and they should not
            // also try to redeliver as we will then do that twice
            exchange.setProperty(Exchange.REDELIVERY_EXHAUSTED, Boolean.TRUE);
            exchange.setException(e);
        }
    }

    protected void doProcessParallel(final AtomicExchange result, Iterable<ProcessorExchangePair> pairs, boolean streaming) throws InterruptedException, ExecutionException {
        final CompletionService<Exchange> completion;
        final AtomicBoolean running = new AtomicBoolean(true);

        if (streaming) {
            // execute tasks in parallel+streaming and aggregate in the order they are finished (out of order sequence)
            completion = new ExecutorCompletionService<Exchange>(executorService);
        } else {
            // execute tasks in parallel and aggregate in the order the tasks are submitted (in order sequence)
            completion = new SubmitOrderedCompletionService<Exchange>(executorService);
        }

        final AtomicInteger total =  new AtomicInteger(0);

        for (ProcessorExchangePair pair : pairs) {
            final Processor producer = pair.getProcessor();
            final Exchange subExchange = pair.getExchange();
            updateNewExchange(subExchange, total.intValue(), pairs);

            completion.submit(new Callable<Exchange>() {
                public Exchange call() throws Exception {
                    if (!running.get()) {
                        // do not start processing the task if we are not running
                        return subExchange;
                    }

                    doProcess(producer, subExchange);

                    // should we stop in case of an exception occurred during processing?
                    if (stopOnException && subExchange.getException() != null) {
                        // signal to stop running
                        running.set(false);
                        throw new CamelExchangeException("Parallel processing failed for number " + total.intValue(), subExchange, subExchange.getException());
                    }

                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Parallel processing complete for exchange: " + subExchange);
                    }
                    return subExchange;
                }
            });

            total.incrementAndGet();
        }

        for (int i = 0; i < total.intValue(); i++) {
            Future<Exchange> future = completion.take();
            Exchange subExchange = future.get();
            if (aggregationStrategy != null) {
                doAggregate(result, subExchange);
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Done parallel processing " + total + " exchanges");
        }
    }

    protected void doProcessSequential(AtomicExchange result, Iterable<ProcessorExchangePair> pairs) throws Exception {
        int total = 0;

        for (ProcessorExchangePair pair : pairs) {
            Processor producer = pair.getProcessor();
            Exchange subExchange = pair.getExchange();
            updateNewExchange(subExchange, total, pairs);

            doProcess(producer, subExchange);

            // should we stop in case of an exception occurred during processing?
            if (stopOnException && subExchange.getException() != null) {
                throw new CamelExchangeException("Sequential processing failed for number " + total, subExchange, subExchange.getException());
            }

            if (LOG.isTraceEnabled()) {
                LOG.trace("Sequential processing complete for number " + total + " exchange: " + subExchange);
            }

            if (aggregationStrategy != null) {
                doAggregate(result, subExchange);
            }
            total++;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Done sequential processing " + total + " exchanges");
        }
    }

    private void doProcess(Processor producer, Exchange exchange) {
        TracedRouteNodes traced = exchange.getUnitOfWork() != null ? exchange.getUnitOfWork().getTracedRouteNodes() : null;

        try {
            // prepare tracing starting from a new block
            if (traced != null) {
                traced.pushBlock();
            }

            // set property which endpoint we send to
            setToEndpoint(exchange, producer);

            if (exchange.getUnitOfWork() != null && exchange.getUnitOfWork().getRouteContext() != null) {
                // wrap the producer in error handler so we have fine grained error handling on
                // the output side instead of the input side
                // this is needed to support redelivery on that output alone and not doing redelivery
                // for the entire multicast block again which will start from scratch again
                RouteContext routeContext = exchange.getUnitOfWork().getRouteContext();
                ErrorHandlerBuilder builder = routeContext.getRoute().getErrorHandlerBuilder();

                // create error handler (create error handler directly to keep it light weight,
                // instead of using ProcessorDefinition.wrapInErrorHandler)
                producer = builder.createErrorHandler(routeContext, producer);
            }

            // let the producer process it
            producer.process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // pop the block so by next round we have the same staring point and thus the tracing looks accurate
            if (traced != null) {
                traced.popBlock();
            }
        }
    }

    /**
     * Aggregate the {@link Exchange} with the current result
     *
     * @param result the current result
     * @param exchange the exchange to be added to the result
     */
    protected synchronized void doAggregate(AtomicExchange result, Exchange exchange) {
        if (aggregationStrategy != null) {
            // prepare the exchanges for aggregation
            Exchange oldExchange = result.get();
            ExchangeHelper.prepareAggregation(oldExchange, exchange);
            result.set(aggregationStrategy.aggregate(oldExchange, exchange));
        }
    }

    protected void updateNewExchange(Exchange exchange, int index, Iterable<ProcessorExchangePair> allPairs) {
        exchange.setProperty(Exchange.MULTICAST_INDEX, index);
    }

    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange) {
        List<ProcessorExchangePair> result = new ArrayList<ProcessorExchangePair>(processors.size());

        for (Processor processor : processors) {
            Exchange copy = exchange.copy();
            result.add(new ProcessorExchangePair(processor, copy));
        }
        return result;
    }

    protected void doStop() throws Exception {
        if (executorService != null) {
            executorService.shutdown();
            executorService.awaitTermination(0, TimeUnit.SECONDS);
            executorService = null;
        }
        ServiceHelper.stopServices(processors);
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processors);
    }
    
    private static void setToEndpoint(Exchange exchange, Processor processor) {
        if (processor instanceof Producer) {
            Producer producer = (Producer) processor;
            exchange.setProperty(Exchange.TO_ENDPOINT, producer.getEndpoint().getEndpointUri());
        }
    }

    /**
     * Is the multicast processor working in streaming mode?
     * 
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

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    public boolean isParallelProcessing() {
        return isParallelProcessing;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
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
