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
package org.apache.camel.micrometer.observability;

import java.util.concurrent.CompletableFuture;

import io.micrometer.tracing.Tracer;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.telemetry.Span;
import org.apache.camel.telemetry.SpanStorageManagerExchange;

/**
 * TraceProcessorsMicrometerObsInterceptStrategy is used to wrap each processor calls and generate the scope required
 * for any custom Micrometer Observability activity in the processor to be available.
 */
public class TraceProcessorsMicrometerObsInterceptStrategy implements InterceptStrategy {

    // NOTE: this is an implementation detail that the interceptor should not know.
    // We are temporarily using this to evaluate as a patch for a context leak problem we're suffering.
    // Once we are clear this is correctly fixed and no more corner cases, then, we should change the TraceProcessorsInterceptStrategy
    // class in camel-telemetry component in order to be able to retrieve the span before executing the processor and perform
    // a similar logic of what we're doing here.
    private SpanStorageManagerExchange spanStorage = new SpanStorageManagerExchange();

    private final Tracer tracer;

    public TraceProcessorsMicrometerObsInterceptStrategy(Tracer micrometerObsTracer) {
        this.tracer = micrometerObsTracer;
    }

    @Override
    public Processor wrapProcessorInInterceptors(
            CamelContext camelContext,
            NamedNode processorDefinition, Processor target, Processor nextTarget)
            throws Exception {
        return new TraceProcessor(target);
    }

    private class TraceProcessor extends DelegateAsyncProcessor {

        public TraceProcessor(Processor target) {
            super(target);
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            Span activeSpan = spanStorage.peek(exchange);
            if (activeSpan != null) {
                MicrometerObservabilitySpanAdapter otelSpan = (MicrometerObservabilitySpanAdapter) activeSpan;
                try (Tracer.SpanInScope scope = tracer.withSpan(otelSpan.getSpan())) {
                    processor.process(exchange);
                }
            } else {
                processor.process(exchange);
            }
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            Span activeSpan = spanStorage.peek(exchange);
            if (activeSpan != null) {
                MicrometerObservabilitySpanAdapter otelSpan = (MicrometerObservabilitySpanAdapter) activeSpan;
                try (Tracer.SpanInScope scope = tracer.withSpan(otelSpan.getSpan())) {
                    return processor.process(exchange, doneSync -> {
                        callback.done(doneSync);
                    });
                }
            } else {
                return processor.process(exchange, doneSync -> {
                    callback.done(doneSync);
                });
            }
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
