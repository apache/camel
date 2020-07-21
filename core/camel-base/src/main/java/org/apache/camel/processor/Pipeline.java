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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.spi.ReactiveExecutor;
import org.apache.camel.spi.RouteIdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * Creates a Pipeline pattern where the output of the previous step is sent as
 * input to the next step, reusing the same message exchanges
 */
public class Pipeline extends AsyncProcessorSupport implements Navigate<Processor>, Traceable, IdAware, RouteIdAware {

    private static final Logger LOG = LoggerFactory.getLogger(Pipeline.class);

    private final CamelContext camelContext;
    private final ReactiveExecutor reactiveExecutor;
    private final List<AsyncProcessor> processors;
    private final int size;
    private String id;
    private String routeId;

    private final class PipelineTask implements Runnable {

        private final Exchange exchange;
        private final AsyncCallback callback;
        private final AtomicInteger index;

        PipelineTask(Exchange exchange, AsyncCallback callback, AtomicInteger index) {
            this.exchange = exchange;
            this.callback = callback;
            this.index = index;
        }

        @Override
        public void run() {
            boolean stop = exchange.isRouteStop();
            int num = index.get();
            boolean more = num < size;
            boolean first = num == 0;

            if (!stop && more && (first || continueProcessing(exchange, "so breaking out of pipeline", LOG))) {

                // prepare for next run
                if (exchange.hasOut()) {
                    exchange.setIn(exchange.getOut());
                    exchange.setOut(null);
                }

                // get the next processor
                AsyncProcessor processor = processors.get(index.getAndIncrement());

                processor.process(exchange, doneSync -> reactiveExecutor.schedule(this));
            } else {
                ExchangeHelper.copyResults(exchange, exchange);

                // logging nextExchange as it contains the exchange that might have altered the payload and since
                // we are logging the completion if will be confusing if we log the original instead
                // we could also consider logging the original and the nextExchange then we have *before* and *after* snapshots
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);
                }

                reactiveExecutor.schedule(callback);
            }
        }
    }

    public Pipeline(CamelContext camelContext, Collection<Processor> processors) {
        this.camelContext = camelContext;
        this.reactiveExecutor = camelContext.adapt(ExtendedCamelContext.class).getReactiveExecutor();
        this.processors = processors.stream().map(AsyncProcessorConverterHelper::convert).collect(Collectors.toList());
        this.size = processors.size();
    }

    public static Processor newInstance(CamelContext camelContext, List<Processor> processors) {
        if (processors.isEmpty()) {
            return null;
        } else if (processors.size() == 1) {
            return processors.get(0);
        }
        return new Pipeline(camelContext, processors);
    }

    public static Processor newInstance(final CamelContext camelContext, final Processor... processors) {
        if (processors == null || processors.length == 0) {
            return null;
        } else if (processors.length == 1) {
            return processors[0];
        }

        final List<Processor> toBeProcessed = new ArrayList<>(processors.length);
        for (Processor processor : processors) {
            if (processor != null) {
                toBeProcessed.add(processor);
            }
        }

        return new Pipeline(camelContext, toBeProcessed);
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        // create task which has state used during routing
        PipelineTask task = new PipelineTask(exchange, callback, new AtomicInteger());

        if (exchange.isTransacted()) {
            reactiveExecutor.scheduleSync(task);
        } else {
            reactiveExecutor.scheduleMain(task);
        }
        return false;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(processors);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(processors);
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public String getTraceLabel() {
        return "pipeline";
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
