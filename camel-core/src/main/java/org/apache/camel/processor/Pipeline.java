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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Traceable;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ReactiveHelper;
import org.apache.camel.support.service.ServiceHelper;

import static org.apache.camel.processor.PipelineHelper.continueProcessing;

/**
 * Creates a Pipeline pattern where the output of the previous step is sent as
 * input to the next step, reusing the same message exchanges
 */
public class Pipeline extends AsyncProcessorSupport implements Navigate<Processor>, Traceable, IdAware {

    private final CamelContext camelContext;
    private List<AsyncProcessor> processors;
    private String id;

    public Pipeline(CamelContext camelContext, Collection<Processor> processors) {
        this.camelContext = camelContext;
        this.processors = processors.stream().map(AsyncProcessorConverterHelper::convert).collect(Collectors.toList());
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
        if (exchange.isTransacted()) {
            ReactiveHelper.scheduleSync(() -> Pipeline.this.doProcess(exchange, callback, processors.iterator(), true),
                    "Step[" + exchange.getExchangeId() + "," + Pipeline.this + "]");
        } else {
            ReactiveHelper.scheduleMain(() -> Pipeline.this.doProcess(exchange, callback, processors.iterator(), true),
                    "Step[" + exchange.getExchangeId() + "," + Pipeline.this + "]");
        }
        return false;
    }

    protected void doProcess(Exchange exchange, AsyncCallback callback, Iterator<AsyncProcessor> processors, boolean first) {
        if (continueRouting(processors, exchange)
                && (first || continueProcessing(exchange, "so breaking out of pipeline", log))) {

            // prepare for next run
            if (exchange.hasOut()) {
                exchange.setIn(exchange.getOut());
                exchange.setOut(null);
            }

            // get the next processor
            AsyncProcessor processor = processors.next();

            processor.process(exchange, doneSync ->
                    ReactiveHelper.schedule(() -> doProcess(exchange, callback, processors, false),
                            "Step[" + exchange.getExchangeId() + "," + Pipeline.this + "]"));
        } else {
            ExchangeHelper.copyResults(exchange, exchange);

            // logging nextExchange as it contains the exchange that might have altered the payload and since
            // we are logging the completion if will be confusing if we log the original instead
            // we could also consider logging the original and the nextExchange then we have *before* and *after* snapshots
            log.trace("Processing complete for exchangeId: {} >>> {}", exchange.getExchangeId(), exchange);

            ReactiveHelper.callback(callback);
        }
    }

    protected boolean continueRouting(Iterator<AsyncProcessor> it, Exchange exchange) {
        Object stop = exchange.getProperty(Exchange.ROUTE_STOP);
        if (stop != null) {
            boolean doStop = exchange.getContext().getTypeConverter().convertTo(Boolean.class, stop);
            if (doStop) {
                log.debug("ExchangeId: {} is marked to stop routing: {}", exchange.getExchangeId(), exchange);
                return false;
            }
        }
        // continue if there are more processors to route
        boolean answer = it.hasNext();
        log.trace("ExchangeId: {} should continue routing: {}", exchange.getExchangeId(), answer);
        return answer;
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
        return "Pipeline[" + getProcessors() + "]";
    }

    public List<Processor> getProcessors() {
        return (List) processors;
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

    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        return new ArrayList<>(processors);
    }

    public boolean hasNext() {
        return processors != null && !processors.isEmpty();
    }
}
