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
package org.apache.camel.telemetry;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.processor.DelegateAsyncProcessor;

/**
 * TraceProcessorsInterceptStrategy is used to wrap each processor calls and generate a Span for each process execution.
 */
public class TraceProcessorsInterceptStrategy implements InterceptStrategy {

    private Tracer tracer;

    public TraceProcessorsInterceptStrategy(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Processor wrapProcessorInInterceptors(
            CamelContext camelContext,
            NamedNode processorDefinition, Processor target, Processor nextTarget)
            throws Exception {
        return new TraceProcessor(target, processorDefinition);
    }

    private class TraceProcessor extends DelegateAsyncProcessor {
        private final NamedNode processorDefinition;

        public TraceProcessor(Processor target, NamedNode processorDefinition) {
            super(target);
            this.processorDefinition = processorDefinition;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            String processorName = processorDefinition.getId() + "-" + processorDefinition.getShortName();
            if (tracer.isTraceProcessors() && !tracer.exclude(processorName, exchange.getContext())) {
                tracer.beginProcessorSpan(exchange, processorName);
                try {
                    processor.process(exchange);
                } finally {
                    tracer.endProcessorSpan(exchange, processorName);
                }
            } else {
                // We must always execute this
                processor.process(exchange);
            }
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            String processorName = processorDefinition.getId() + "-" + processorDefinition.getShortName();
            boolean isTraceProcessor = tracer.isTraceProcessors() && !tracer.exclude(processorName, exchange.getContext());
            if (isTraceProcessor) {
                try {
                    tracer.beginProcessorSpan(exchange, processorName);
                } catch (Exception e) {
                    exchange.setException(e);
                }
            }
            return processor.process(exchange, doneSync -> {
                try {
                    callback.done(doneSync);
                } finally {
                    if (isTraceProcessor) {
                        try {
                            tracer.endProcessorSpan(exchange, processorName);
                        } catch (Exception e) {
                            exchange.setException(e);
                        }
                    }
                }
            });
        }

        @Override
        public CompletableFuture<Exchange> processAsync(Exchange exchange) {
            AsyncCallbackToCompletableFutureAdapter<Exchange> callback
                    = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
            process(exchange, callback);
            return callback.getFuture();
        }
    }

}
