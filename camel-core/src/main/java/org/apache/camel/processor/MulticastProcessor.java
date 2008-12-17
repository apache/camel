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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.concurrent.AtomicExchange;
import org.apache.camel.util.concurrent.CountingLatch;

import static org.apache.camel.util.ObjectHelper.notNull;

/**
 * Implements the Multicast pattern to send a message exchange to a number of
 * endpoints, each endpoint receiving a copy of the message exchange.
 *
 * @see Pipeline
 * @version $Revision$
 */
public class MulticastProcessor extends ServiceSupport implements Processor {
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
    private AggregationStrategy aggregationStrategy;
    private boolean isParallelProcessing;
    private ThreadPoolExecutor executor;
    private final boolean streaming;
    private final AtomicBoolean shutdown = new AtomicBoolean(true);

    public MulticastProcessor(Collection<Processor> processors) {
        this(processors, null);
    }

    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy) {
        this(processors, aggregationStrategy, false, null);
    }
    
    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy, boolean parallelProcessing, ThreadPoolExecutor executor) {
        this(processors, aggregationStrategy, parallelProcessing, executor, false);
    }

    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy, boolean parallelProcessing, ThreadPoolExecutor executor, boolean streaming) {
        notNull(processors, "processors");
        this.processors = processors;
        this.aggregationStrategy = aggregationStrategy;
        this.isParallelProcessing = parallelProcessing;
        if (isParallelProcessing) {
            if (executor != null) {
                this.executor = executor;
            } else { 
                // setup default Executor
                this.executor = new ThreadPoolExecutor(processors.size(), processors.size(), 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(processors.size()));
            }
        }
        this.streaming = streaming;
    }

    /**
     * A helper method to convert a list of endpoints into a list of processors
     */
    public static <E extends Exchange> Collection<Processor> toProducers(Collection<Endpoint> endpoints)
        throws Exception {
        Collection<Processor> answer = new ArrayList<Processor>();
        for (Endpoint endpoint : endpoints) {
            answer.add(endpoint.createProducer());
        }
        return answer;
    }

    @Override
    public String toString() {
        return "Multicast" + getProcessors();
    }

    class ProcessCall implements Runnable {
        private final Exchange exchange;
        private final AsyncCallback callback;
        private final Processor processor;

        public ProcessCall(Exchange exchange, Processor processor, AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
            this.processor = processor;
        }

        public void run() {
            if (shutdown.get()) {
                exchange.setException(new RejectedExecutionException());
                callback.done(false);
            } else {
                try {
                    processor.process(exchange);
                } catch (Exception ex) {
                    exchange.setException(ex);
                }
                callback.done(false);
            }
        }
    }

    public void process(Exchange exchange) throws Exception {
        final AtomicExchange result = new AtomicExchange();

        Iterable<ProcessorExchangePair> pairs = createProcessorExchangePairs(exchange);
        
        // Parallel Processing the producer
        if (isParallelProcessing) {
            List<Exchange> exchanges = new LinkedList<Exchange>();
            final CountingLatch completedExchanges = new CountingLatch();
            int i = 0;
            for (ProcessorExchangePair pair : pairs) {
                Processor producer = pair.getProcessor();
                final Exchange subExchange = pair.getExchange();
                updateNewExchange(subExchange, i, pairs);
                exchanges.add(subExchange);
                completedExchanges.increment(); 
                ProcessCall call = new ProcessCall(subExchange, producer, new AsyncCallback() {
                    public void done(boolean doneSynchronously) {
                        if (streaming && aggregationStrategy != null) {
                            doAggregate(result, subExchange);
                        }
                        completedExchanges.decrement();
                    }

                });
                executor.execute(call);
                i++;
            }
            completedExchanges.await();
            if (!streaming && aggregationStrategy != null) {
                for (Exchange resultExchange : exchanges) {
                    doAggregate(result, resultExchange);
                }
            }

        } else {
            // we call the producer one by one sequentially
            int i = 0;
            for (ProcessorExchangePair pair : pairs) {
                Processor producer = pair.getProcessor();
                Exchange subExchange = pair.getExchange();
                updateNewExchange(subExchange, i, pairs);
                try {
                    producer.process(subExchange);
                } catch (Exception exception) {
                    subExchange.setException(exception);
                }
                doAggregate(result, subExchange);
                i++;
            }
        }
        if (result.get() != null) {
            ExchangeHelper.copyResults(exchange, result.get());
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
            if (result.get() == null) {
                result.set(exchange);
            } else {
                result.set(aggregationStrategy.aggregate(result.get(), exchange));
            }
        }
    }

    protected void updateNewExchange(Exchange exchange, int i, Iterable<ProcessorExchangePair> allPairs) {
        // No updates needed
    }

    protected Iterable<ProcessorExchangePair> createProcessorExchangePairs(Exchange exchange) {
        List<ProcessorExchangePair> result = new ArrayList<ProcessorExchangePair>(processors.size());
        Processor[] processorsArray = processors.toArray(new Processor[processors.size()]);
        for (int i = 0; i < processorsArray.length; i++) {
            result.add(new ProcessorExchangePair(processorsArray[i], exchange.copy()));
        }
        return result;
    }

    protected void doStop() throws Exception {
        shutdown.set(true);
        if (executor != null) {
            executor.shutdown();
            executor.awaitTermination(0, TimeUnit.SECONDS);
        }
        ServiceHelper.stopServices(processors);
    }

    protected void doStart() throws Exception {
        shutdown.set(false);
        if (executor != null) {
            executor.setRejectedExecutionHandler(new RejectedExecutionHandler() {
                public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
                    ProcessCall call = (ProcessCall)runnable;
                    call.exchange.setException(new RejectedExecutionException());
                    call.callback.done(false);
                }
            });
        }
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
    protected boolean isStreaming() {
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
}
