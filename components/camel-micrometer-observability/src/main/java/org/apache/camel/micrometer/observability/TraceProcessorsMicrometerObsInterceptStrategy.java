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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.micrometer.tracing.BaggageInScope;
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
                MicrometerObservabilitySpanAdapter microObsSpan = (MicrometerObservabilitySpanAdapter) activeSpan;
                try (Tracer.SpanInScope scope = tracer.withSpan(microObsSpan.getSpan());
                     ScopedBaggages scopedBaggages = new ScopedBaggages(getBaggageFromProperties(exchange))) {
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
                MicrometerObservabilitySpanAdapter microObsSpan = (MicrometerObservabilitySpanAdapter) activeSpan;
                try (Tracer.SpanInScope scope = tracer.withSpan(microObsSpan.getSpan());
                     ScopedBaggages scopedBaggages = new ScopedBaggages(getBaggageFromProperties(exchange))) {
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

        // We inspect the exchange in order to find any baggage variable
        private List<BaggageInScope> getBaggageFromProperties(Exchange exchange) {
            List<BaggageInScope> baggages = new ArrayList<>();

            for (String propertyKey : exchange.getProperties().keySet()) {
                String key = getBaggageVar(propertyKey);
                if (key != null) {
                    String value = exchange.getProperty(propertyKey) == null
                            ? null : exchange.getProperty(propertyKey).toString();
                    baggages.add(tracer.createBaggageInScope(key, value));
                }
            }

            return baggages;
        }

        private String getBaggageVar(String key) {
            if (key == null || !key.startsWith(org.apache.camel.telemetry.Tracer.BAGGAGE_PROPERTY)) {
                return null;
            }

            return key.substring(org.apache.camel.telemetry.Tracer.BAGGAGE_PROPERTY.length());
        }
    }
}

class ScopedBaggages implements AutoCloseable {

    private final List<BaggageInScope> baggages;

    public ScopedBaggages(List<BaggageInScope> baggages) {
        this.baggages = new ArrayList<>(baggages);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (int i = baggages.size() - 1; i >= 0; i--) {
            try {
                baggages.get(i).close();
            } catch (Exception e) {
                if (failure == null) {
                    failure = new RuntimeException(
                            "Failed to close baggage scopes", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }

        if (failure != null) {
            throw failure;
        }
    }
}
