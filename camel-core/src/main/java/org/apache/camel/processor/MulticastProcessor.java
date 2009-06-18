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

import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.AggregationStrategy;
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
public class MulticastProcessor extends ServiceSupport implements Processor, Navigate {

    private static final transient Log LOG = LogFactory.getLog(MulticastProcessor.class);

    // TODO: Add option to stop if an exception was thrown during processing to break asap (future task cancel)

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

    private final Collection<Processor> processors;
    private final AggregationStrategy aggregationStrategy;
    private final boolean isParallelProcessing;
    private final boolean streaming;
    private ExecutorService executorService;

    public MulticastProcessor(Collection<Processor> processors) {
        this(processors, null);
    }

    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy) {
        this(processors, aggregationStrategy, false, null, false);
    }
    
    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy, boolean parallelProcessing, ExecutorService executorService, boolean streaming) {
        notNull(processors, "processors");
        // TODO: end() does not work correctly with Splitter
        this.processors = processors;
        this.aggregationStrategy = aggregationStrategy;
        this.isParallelProcessing = parallelProcessing;
        this.executorService = executorService;
        this.streaming = streaming;

        if (isParallelProcessing()) {
            if (this.executorService == null) {
                // setup default executor as parallel processing requires an executor
                this.executorService = ExecutorServiceHelper.newScheduledThreadPool(5, "Multicast", true);
            }
        }
    }

    @Override
    public String toString() {
        return "Multicast[" + getProcessors() + "]";
    }

    public void process(Exchange exchange) throws Exception {
        final AtomicExchange result = new AtomicExchange();
        final Iterable<ProcessorExchangePair> pairs = createProcessorExchangePairs(exchange);

        if (isParallelProcessing()) {
            doProcessParallel(result, pairs, isStreaming());
        } else {
            doProcessSequntiel(result, pairs);
        }

        if (result.get() != null) {
            ExchangeHelper.copyResults(exchange, result.get());
        }
    }

    protected void doProcessParallel(final AtomicExchange result, Iterable<ProcessorExchangePair> pairs, boolean streaming) throws InterruptedException, ExecutionException {
        CompletionService<Exchange> completion;
        if (streaming) {
            // execute tasks in paralle+streaming and aggregate in the order they are finished (out of order sequence)
            completion = new ExecutorCompletionService<Exchange>(executorService);
        } else {
            // execute tasks in parallel and aggregate in the order the tasks are submitted (in order sequence)
            completion = new SubmitOrderedCompletionService<Exchange>(executorService);
        }
        int total = 0;

        for (ProcessorExchangePair pair : pairs) {
            final Processor producer = pair.getProcessor();
            final Exchange subExchange = pair.getExchange();
            updateNewExchange(subExchange, total, pairs);

            completion.submit(new Callable<Exchange>() {
                public Exchange call() throws Exception {
                    try {
                        producer.process(subExchange);
                    } catch (Exception e) {
                        subExchange.setException(e);
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Parallel processing complete for exchange: " + subExchange);
                    }
                    return subExchange;
                }
            });

            total++;
        }

        for (int i = 0; i < total; i++) {
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

    protected void doProcessSequntiel(AtomicExchange result, Iterable<ProcessorExchangePair> pairs) throws Exception {
        int total = 0;

        for (ProcessorExchangePair pair : pairs) {
            Processor producer = pair.getProcessor();
            Exchange subExchange = pair.getExchange();
            updateNewExchange(subExchange, total, pairs);

            // process it sequentially
            producer.process(subExchange);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Sequientel processing complete for number " + total + " exchange: " + subExchange);
            }

            if (aggregationStrategy != null) {
                doAggregate(result, subExchange);
            }
            total++;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Done sequientel processing " + total + " exchanges");
        }
    }

    /**
     * Aggregate the {@link Exchange} with the current result
     *
     * @param result the current result
     * @param exchange the exchange to be added to the result
     */
    protected synchronized void doAggregate(AtomicExchange result, Exchange exchange) {
        // only aggregate if the exchange is not filtered (eg by the FilterProcessor)
        Boolean filtered = exchange.getProperty(Exchange.FILTERED, Boolean.class);
        if (aggregationStrategy != null && (filtered == null || !filtered)) {
            result.set(aggregationStrategy.aggregate(result.get(), exchange));
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Cannot aggregate exchange as its filtered: " + exchange);
            }
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
        }
        ServiceHelper.stopServices(processors);
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(processors);
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
