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

import static org.apache.camel.util.ObjectHelper.notNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
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

/**
 * Implements the Multicast pattern to send a message exchange to a number of
 * endpoints, each endpoint receiving a copy of the message exchange.
 *
 * @see Pipeline
 * @version $Revision$
 */
public class MulticastProcessor extends ServiceSupport implements Processor {
    private Collection<Processor> processors;
    private AggregationStrategy aggregationStrategy;
    private boolean isParallelProcessing;
    private ThreadPoolExecutor executor;
    private final AtomicBoolean shutdown = new AtomicBoolean(true);

    public MulticastProcessor(Collection<Processor> processors) {
        this(processors, null);
    }

    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy) {
        this(processors, aggregationStrategy, false, null);
    }

    public MulticastProcessor(Collection<Processor> processors, AggregationStrategy aggregationStrategy, boolean parallelProcessing, ThreadPoolExecutor executor) {
        notNull(processors, "processors");
        this.processors = processors;
        this.aggregationStrategy = aggregationStrategy;
        this.isParallelProcessing = parallelProcessing;
        if (isParallelProcessing) {
            if (executor != null) {
                this.executor = executor;
            } else {// setup default Executor
                this.executor = new ThreadPoolExecutor(1, processors.size(), 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(processors.size()));
            }

        }

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
            if( shutdown.get() ) {
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
        Exchange result = null;
        // Parallel Processing the producer
        if (isParallelProcessing) {
            Exchange[] exchanges = new Exchange[processors.size()];
            final CountDownLatch completedExchanges = new CountDownLatch(exchanges.length);
            int i = 0;
            for (Processor producer : processors) {
                exchanges[i] = copyExchangeStrategy(producer, exchange);
                ProcessCall call = new ProcessCall(exchanges[i], producer, new AsyncCallback(){
                    public void done(boolean doneSynchronously) {
                        completedExchanges.countDown();
                    }

                });
                executor.execute(call);
                i++;
            }
            completedExchanges.await();
            if (aggregationStrategy != null) {
                for (Exchange resultExchange: exchanges) {
                    if (result == null) {
                        result = resultExchange;
                    } else {
                        result = aggregationStrategy.aggregate(result, resultExchange);
                    }
                }
            }

        } else {
            // we call the producer one by one sequentially
            for (Processor producer : processors) {
                Exchange copy = copyExchangeStrategy(producer, exchange);
                producer.process(copy);
                if (aggregationStrategy != null) {
                    if (result == null) {
                        result = copy;
                    } else {
                        result = aggregationStrategy.aggregate(result, copy);
                    }
                }
            }
        }
        if (result != null) {
            ExchangeHelper.copyResults(exchange, result);
        }
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
     * Returns the producers to multicast to
     */
    public Collection<Processor> getProcessors() {
        return processors;
    }

    /**
     * Strategy method to copy the exchange before sending to another endpoint.
     * Derived classes such as the {@link Pipeline} will not clone the exchange
     *
     * @param processor the processor that will send the exchange
     * @param exchange
     * @return the current exchange if no copying is required such as for a
     *         pipeline otherwise a new copy of the exchange is returned.
     */
    protected Exchange copyExchangeStrategy(Processor processor, Exchange exchange) {
        return exchange.copy();
    }
}
